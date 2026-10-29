package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketIssuanceRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Plafond de billets par émission. Borne la durée de la transaction (une
     * insertion et une signature par billet, catégorie verrouillée pendant ce
     * temps) et la taille d'une commande. Le module orders devra appliquer la
     * même limite à la création de la commande.
     */
    public static final int QUANTITE_MAX_PAR_EMISSION = 20;

    private final TicketCategoryRepository categoryRepository;
    private final TicketIssuanceRepository issuanceRepository;
    private final TicketRepository ticketRepository;
    private final QRSigner qrSigner;

    public IssueTicketUseCase(TicketCategoryRepository categoryRepository,
                              TicketIssuanceRepository issuanceRepository,
                              TicketRepository ticketRepository,
                              QRSigner qrSigner) {
        this.categoryRepository = categoryRepository;
        this.issuanceRepository = issuanceRepository;
        this.ticketRepository = ticketRepository;
        this.qrSigner = qrSigner;
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
     * @throws IllegalArgumentException si un paramètre est absent, si la quantité n'est pas strictement
     *                                  positive ou si elle dépasse {@link #QUANTITE_MAX_PAR_EMISSION}
     * @throws NoSuchElementException   si la catégorie n'existe pas
     * @throws BilletsDejaEmisException si des billets de cette catégorie ont déjà été émis pour la commande
     * @throws QuotaDepasseException    si la quantité dépasse le quota restant
     */
    @Transactional
    public List<IssuedTicket> execute(UUID categoryId, UUID orderId, int quantite) {
        if (categoryId == null || orderId == null) {
            throw new IllegalArgumentException("categoryId et orderId sont obligatoires");
        }
        // Vérifiée ici, et pas seulement par TicketCategory.reserver : la quantité
        // est écrite dans le registre d'émission AVANT la réservation, et une
        // quantité invalide y violerait la contrainte CHECK (erreur 500, pas 400).
        if (quantite <= 0 || quantite > QUANTITE_MAX_PAR_EMISSION) {
            throw new IllegalArgumentException(
                "La quantité doit être comprise entre 1 et %d".formatted(QUANTITE_MAX_PAR_EMISSION));
        }

        TicketCategory category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("Catégorie de billet introuvable : " + categoryId));

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
        return emis;
    }
}
