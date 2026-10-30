package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.DelaiAttenteVerrous;
import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.MotifSansBillet;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.api.publique.EmissionBillets;
import com.billetplus.tickets.api.publique.PlacesEnVente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — traite une notification de paiement
 * AUTHENTIFIÉE (signature vérifiée par LecteurNotificationsPaiement) : confirme la
 * commande et émet ses billets, ou la classe à rembourser. Règle d'or : un
 * paiement reçu n'est jamais perdu.
 *
 * Paiement reçu (PAYE), pour une commande retrouvée par sa référence (champ signé,
 * ADR 0007) :
 * - déjà PAYEE ou PAYEE_SANS_BILLET → rien (idempotence : un webhook rejoué ne change rien) ;
 * - montant différent de celui de la commande → PAYEE_SANS_BILLET (MONTANT_INCORRECT) ;
 * - commande annulée → PAYEE_SANS_BILLET (PAIEMENT_APRES_ANNULATION) ;
 * - délai dépassé ou commande expirée → PAYEE_SANS_BILLET (RESERVATION_EXPIREE) ;
 * - sinon, transaction A : marquerPayee + émission de chaque ligne (ordre croissant de
 *   categoryId, lock_timeout). Si A échoue pour une raison MÉTIER (vente fermée, quota
 *   épuisé), elle est annulée en entier et une transaction B, séparée, enregistre
 *   PAYEE_SANS_BILLET. On ne rattrape jamais l'exception dans A (UnexpectedRollbackException).
 *   Un conflit de version est relancé, {@value #TENTATIVES_MAX} fois au plus.
 *   Une panne technique (base, signature QR) est remontée telle quelle : le webhook
 *   répond 5xx et pourra être rejoué, rien n'est classé « sans billet » à tort.
 *
 * Paiement échoué ou expiré (ECHOUE) : commande en attente → annulée et ses places
 * rendues ; sinon rien. Référence inconnue : journalisée en ERROR (paiement orphelin,
 * voir OuvrirPaiementUseCase), sans erreur pour la passerelle.
 */
@Service
public class ConfirmerPaiementUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmerPaiementUseCase.class);
    static final int TENTATIVES_MAX = 3;
    private static final Duration DELAI_VERROUS = Duration.ofSeconds(5);

    public enum Resultat {
        /** Commande payée, billets émis. */
        BILLETS_EMIS,
        /** Paiement conservé, billets impossibles : à rembourser (décision B). */
        A_REMBOURSER,
        /** Commande annulée après un échec définitif du paiement. */
        ANNULEE,
        /** Rien à faire : déjà traité, ou événement sans effet. */
        SANS_EFFET,
        /** Aucune commande pour cette référence (paiement orphelin). */
        COMMANDE_INCONNUE
    }

    private final OrderRepository orderRepository;
    private final EmissionBillets emissionBillets;
    private final PlacesEnVente placesEnVente;
    private final DelaiAttenteVerrous delaiAttenteVerrous;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public ConfirmerPaiementUseCase(OrderRepository orderRepository, EmissionBillets emissionBillets,
                                    PlacesEnVente placesEnVente, DelaiAttenteVerrous delaiAttenteVerrous,
                                    PlatformTransactionManager transactionManager, Clock clock) {
        this.orderRepository = orderRepository;
        this.emissionBillets = emissionBillets;
        this.placesEnVente = placesEnVente;
        this.delaiAttenteVerrous = delaiAttenteVerrous;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Resultat traiter(NotificationPaiement notification) {
        Optional<Order> trouvee = orderRepository.findByReferencePaiement(notification.reference());
        if (trouvee.isEmpty()) {
            if (notification.type() == NotificationPaiement.Type.PAYE) {
                LOG.error("Paiement reçu pour une référence sans commande ({}, {} FCFA) : à rembourser à la main",
                    notification.reference(), notification.montant());
            }
            return Resultat.COMMANDE_INCONNUE;
        }
        return switch (notification.type()) {
            case PAYE -> paiementRecu(trouvee.get(), notification);
            case ECHOUE -> paiementEchoue(trouvee.get());
            case AUTRE -> Resultat.SANS_EFFET;
        };
    }

    private Resultat paiementRecu(Order commande, NotificationPaiement notification) {
        OrderStatus statut = commande.getStatus();
        if (statut == OrderStatus.PAYEE || statut == OrderStatus.PAYEE_SANS_BILLET) {
            return Resultat.SANS_EFFET;
        }
        if (notification.montant() != commande.montantTotal().valeur().longValueExact()) {
            LOG.error("Montant payé ({} FCFA) différent de la commande {} ({}) : à rembourser",
                notification.montant(), commande.getId(), commande.montantTotal());
            return aRembourser(commande, MotifSansBillet.MONTANT_INCORRECT);
        }
        if (statut == OrderStatus.ANNULEE) {
            return aRembourser(commande, MotifSansBillet.PAIEMENT_APRES_ANNULATION);
        }
        if (statut == OrderStatus.EXPIREE || commande.delaiDepasse(Instant.now(clock))) {
            return aRembourser(commande, MotifSansBillet.RESERVATION_EXPIREE);
        }

        for (int tentative = 1; ; tentative++) {
            try {
                transaction.executeWithoutResult(statutTx -> payerEtEmettre(commande));
                return Resultat.BILLETS_EMIS;
            } catch (OptimisticLockingFailureException e) {
                if (tentative >= TENTATIVES_MAX) {
                    throw e;
                }
                LOG.info("Conflit de version à la confirmation de la commande {} : tentative {}",
                    commande.getId(), tentative + 1);
            } catch (BusinessConflictException | NoSuchElementException e) {
                // Transaction A entièrement annulée : aucune trace du paiement n'y reste.
                // Il est enregistré ici, dans une transaction séparée. Le délai a pu
                // expirer entre la première lecture et la transaction A.
                MotifSansBillet motif = commande.delaiDepasse(Instant.now(clock))
                    ? MotifSansBillet.RESERVATION_EXPIREE : motifDeLEchec(commande);
                return aRembourser(commande, motif);
            }
        }
    }

    /** Transaction A : relit la commande (version courante), la paie, émet ses billets. */
    private void payerEtEmettre(Order lue) {
        delaiAttenteVerrous.appliquer(DELAI_VERROUS);
        Order commande = orderRepository.findById(lue.getId())
            .orElseThrow(() -> new NoSuchElementException("Commande introuvable"));
        commande.marquerPayee(Instant.now(clock));
        orderRepository.save(commande);
        commande.getLignes().stream()
            .sorted(Comparator.comparing(OrderLine::categoryId))
            .forEach(ligne -> emissionBillets.emettre(ligne.categoryId(), commande.getId(), ligne.quantite()));
    }

    /**
     * Sans dépendre des exceptions du module tickets : si une catégorie n'est plus
     * en vente, la vente est fermée ; sinon, ce sont les places qui manquaient.
     */
    private MotifSansBillet motifDeLEchec(Order commande) {
        boolean venteFermee = commande.getLignes().stream()
            .anyMatch(l -> placesEnVente.prixSiEnVente(commande.getEventId(), l.categoryId()).isEmpty());
        return venteFermee ? MotifSansBillet.VENTE_FERMEE : MotifSansBillet.PLACES_INDISPONIBLES;
    }

    /** Transaction B : le paiement est enregistré à rembourser, puis les places sont rendues. */
    private Resultat aRembourser(Order lue, MotifSansBillet motif) {
        Order enregistree = transaction.execute(statutTx -> {
            Order commande = orderRepository.findById(lue.getId())
                .orElseThrow(() -> new NoSuchElementException("Commande introuvable"));
            if (commande.getStatus() == OrderStatus.PAYEE || commande.getStatus() == OrderStatus.PAYEE_SANS_BILLET) {
                return null; // traitée entre-temps par une notification concurrente
            }
            commande.marquerPayeeSansBillet(motif, Instant.now(clock));
            return orderRepository.save(commande);
        });
        if (enregistree == null) {
            return Resultat.SANS_EFFET;
        }
        LOG.warn("Commande {} payée sans billet ({}) : à rembourser", enregistree.getId(), motif);
        rendreLesPlaces(enregistree);
        return Resultat.A_REMBOURSER;
    }

    private Resultat paiementEchoue(Order lue) {
        Order annulee = transaction.execute(statutTx -> {
            Order commande = orderRepository.findById(lue.getId())
                .orElseThrow(() -> new NoSuchElementException("Commande introuvable"));
            if (commande.getStatus() != OrderStatus.EN_ATTENTE_PAIEMENT) {
                return null; // déjà payée, annulée ou expirée : un échec tardif ne change rien
            }
            commande.annuler();
            return orderRepository.save(commande);
        });
        if (annulee == null) {
            return Resultat.SANS_EFFET;
        }
        rendreLesPlaces(annulee);
        return Resultat.ANNULEE;
    }

    /** Après validation seulement ; une panne Redis ici laisse la réservation expirer d'elle-même. */
    private void rendreLesPlaces(Order commande) {
        for (OrderLine ligne : commande.getLignes()) {
            try {
                placesEnVente.liberer(ligne.categoryId(), commande.getId());
            } catch (RuntimeException e) {
                LOG.warn("Places non rendues (commande {}, catégorie {}) : la réservation expirera d'elle-même",
                    commande.getId(), ligne.categoryId(), e);
            }
        }
    }
}
