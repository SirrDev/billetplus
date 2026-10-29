package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketIssuanceRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.QuantiteParCommande;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;
import com.billetplus.tickets.domain.exception.VenteFermeeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — émet {@code quantite} billets d'une
 * catégorie pour une commande, en consommant le quota correspondant, puis
 * signe le QR code de chaque billet (ADR 0004).
 *
 * L'invariant anti-overselling vit dans {@link TicketCategory#reserver(int)} ;
 * cette classe ne fait qu'orchestrer.
 *
 * {@code @Transactional} est indispensable : l'enregistrement de l'émission,
 * la réservation et les sauvegardes forment une seule unité atomique (ADR 0003).
 * C'est aussi ce qui rend effectif le verrouillage optimiste de la catégorie
 * (voir {@link TicketCategoryRepository}) : deux émissions concurrentes sur la
 * même catégorie ne peuvent pas toutes deux valider leur réservation.
 *
 * Pas de nouvelle tentative automatique ici : appelé dans la transaction de
 * confirmation de paiement (module orders), un conflit de version marque
 * cette transaction comme à annuler. La relance appartient à la frontière la
 * plus extérieure (traitement du webhook, ou Wave qui renvoie sa notification).
 */
@Service
public class IssueTicketUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(IssueTicketUseCase.class);

    private final TicketCategoryRepository categoryRepository;
    private final TicketIssuanceRepository issuanceRepository;
    private final TicketRepository ticketRepository;
    private final QRSigner qrSigner;
    private final SeatReservationStore reservationStore;
    private final StatutEvenements statutEvenements;

    public IssueTicketUseCase(TicketCategoryRepository categoryRepository,
                              TicketIssuanceRepository issuanceRepository,
                              TicketRepository ticketRepository,
                              QRSigner qrSigner,
                              SeatReservationStore reservationStore,
                              StatutEvenements statutEvenements) {
        this.categoryRepository = categoryRepository;
        this.issuanceRepository = issuanceRepository;
        this.ticketRepository = ticketRepository;
        this.qrSigner = qrSigner;
        this.reservationStore = reservationStore;
        this.statutEvenements = statutEvenements;
    }

    /**
     * <b>Contrat de l'appelant</b> (futur module orders, confirmation de paiement) :
     * <ul>
     *   <li>n'appeler ce cas d'usage que depuis le traitement d'un webhook Wave dont la
     *       signature et l'anti-rejeu ont été vérifiés : aucune route publique ni
     *       organisateur ne doit y mener ;</li>
     *   <li>{@code orderId}, {@code categoryId} et {@code quantite} sont lus dans la
     *       commande en base (commande payée, montant recalculé côté serveur), jamais
     *       dans la notification : un appel avec une mauvaise quantité occupe le couple
     *       (commande, catégorie) et bloque définitivement la vraie émission (409) ;</li>
     *   <li>une commande à plusieurs catégories est émise dans l'ordre croissant de
     *       {@code categoryId} : deux confirmations simultanées dans des ordres
     *       différents pourraient sinon s'attendre mutuellement (interblocage) ;</li>
     *   <li>toute exception annule la transaction englobante : l'idempotence du webhook
     *       (« déjà traité » → 200) se vérifie AVANT d'ouvrir cette transaction, pas en
     *       attrapant {@link BilletsDejaEmisException} ; un conflit de version se relance
     *       à cette même frontière.</li>
     * </ul>
     *
     * @throws IllegalArgumentException si un paramètre est absent ou si la quantité n'est pas
     *                                  comprise entre 1 et {@link QuantiteParCommande#MAX}
     * @throws NoSuchElementException   si la catégorie n'existe pas
     * @throws BilletsDejaEmisException si des billets de cette catégorie ont déjà été émis pour la commande
     * @throws QuotaDepasseException    si la quantité dépasse le quota restant
     * @throws VenteFermeeException     si l'événement n'est plus ouvert à la vente (annulé depuis le
     *                                  paiement, par exemple) : l'appelant doit rembourser
     */
    @Transactional
    public List<IssuedTicket> execute(UUID categoryId, UUID orderId, int quantite) {
        if (categoryId == null || orderId == null) {
            throw new IllegalArgumentException("categoryId et orderId sont obligatoires");
        }
        // Vérifiée ici, et pas seulement par TicketCategory.reserver : la quantité
        // est écrite dans le registre d'émission AVANT la réservation, et une
        // quantité invalide y violerait la contrainte CHECK (erreur 500, pas 400).
        QuantiteParCommande.verifier(quantite);

        TicketCategory category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("Catégorie de billet introuvable : " + categoryId));

        // Avant toute écriture : un événement annulé entre le paiement et sa
        // confirmation ne reçoit plus de billets (ADR 0005).
        if (!statutEvenements.estOuvertALaVente(category.getEventId())) {
            throw new VenteFermeeException();
        }

        // En premier, avant toute réservation : garde d'idempotence atomique
        // (clé primaire de ticket_issuances). Un appel concurrent pour la même
        // commande attend ici la fin de celui-ci, puis échoue s'il a été validé.
        issuanceRepository.enregistrer(orderId, categoryId, quantite);

        // Lève QuotaDepasseException sans modifier la catégorie ; la transaction
        // est alors annulée, registre d'émission compris.
        category.reserver(quantite);
        categoryRepository.save(category);

        List<IssuedTicket> emis = new ArrayList<>(quantite);
        for (int i = 0; i < quantite; i++) {
            Ticket ticket = ticketRepository.save(new Ticket(category.getEventId(), categoryId, orderId));
            String qrContent = qrSigner.sign(ticket.getId(), ticket.getEventId(), ticket.getEmisLe());
            emis.add(new IssuedTicket(ticket, qrContent));
        }

        libererLaReservationApresValidation(categoryId, orderId);
        return emis;
    }

    /**
     * Les places sont désormais vendues (quota_vendu) : la réservation temporaire
     * ferait double compte jusqu'à son expiration. Elle est libérée APRÈS la
     * validation de la transaction, jamais avant : si l'émission est annulée,
     * l'acheteur garde sa réservation pour une nouvelle tentative.
     *
     * Une panne Redis à ce moment n'annule pas la vente déjà validée : la
     * réservation expirera d'elle-même (places bloquées quelques minutes de
     * trop, sans incohérence).
     */
    private void libererLaReservationApresValidation(UUID categoryId, UUID orderId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    reservationStore.liberer(categoryId, orderId);
                } catch (RuntimeException e) {
                    LOG.warn("Réservation non libérée après émission (catégorie {}, commande {}) : "
                        + "elle expirera d'elle-même", categoryId, orderId, e);
                }
            }
        });
    }
}
