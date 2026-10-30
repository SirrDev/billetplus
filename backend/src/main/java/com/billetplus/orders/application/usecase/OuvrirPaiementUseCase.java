package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.orders.domain.Paiement;
import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — ouvre le paiement d'une commande chez
 * la passerelle (ADR 0007) et renvoie le lien à ouvrir par l'acheteur.
 *
 * Trois temps, pour qu'aucun appel réseau ne tienne une transaction PostgreSQL :
 * 1. lecture de la commande par son jeton, et contrôle qu'un paiement peut s'ouvrir ;
 * 2. appel de la passerelle, HORS transaction ;
 * 3. enregistrement du paiement dans une courte transaction, sous verrou de version.
 *
 * Un seul paiement par commande : redemander le même moyen renvoie le même lien
 * (un double clic, ou un rechargement de page, n'ouvre pas un second paiement) ;
 * un autre moyen est refusé (409). Si deux demandes simultanées passent l'étape 1,
 * une seule enregistre son paiement ; l'autre reçoit 409, et le paiement qu'elle a
 * ouvert chez la passerelle reste sans suite (journalisé en WARN, il expire).
 */
@Service
public class OuvrirPaiementUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(OuvrirPaiementUseCase.class);

    /** Ce que l'acheteur reçoit : le lien de paiement, et l'échéance de sa commande. */
    public record LienPaiement(MoyenPaiement moyen, String url, Instant expireLe) {
    }

    private final ConsulterCommandeUseCase consulterCommande;
    private final OrderRepository orderRepository;
    private final PasserellePaiement passerelle;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration margeMinimale;

    public OuvrirPaiementUseCase(ConsulterCommandeUseCase consulterCommande, OrderRepository orderRepository,
                                 PasserellePaiement passerelle, PlatformTransactionManager transactionManager,
                                 Clock clock,
                                 @Value("${billetplus.paiement.marge-minimale:60s}") Duration margeMinimale) {
        this.consulterCommande = consulterCommande;
        this.orderRepository = orderRepository;
        this.passerelle = passerelle;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.margeMinimale = margeMinimale;
    }

    /**
     * @throws NoSuchElementException                (404) commande inconnue ou jeton faux
     * @throws TransitionCommandeInterditeException  (409) commande plus en attente, délai presque
     *                                               écoulé, autre moyen déjà ouvert, commande gratuite
     * @throws com.billetplus.orders.domain.exception.PaiementRefuseException (409) refus de la passerelle
     * @throws com.billetplus.shared.exception.ServiceExterneIndisponibleException (503) passerelle en panne
     */
    public LienPaiement execute(UUID orderId, String jeton, MoyenPaiement moyen) {
        if (moyen == null) {
            throw new IllegalArgumentException("Le moyen de paiement est obligatoire");
        }
        Order commande = consulterCommande.execute(orderId, jeton);

        Paiement existant = commande.getPaiement();
        if (existant != null && existant.moyen() == moyen
            && commande.getStatus() == OrderStatus.EN_ATTENTE_PAIEMENT) {
            return new LienPaiement(moyen, existant.url(), commande.getExpireLe());
        }
        commande.verifierPeutOuvrirUnPaiement(Instant.now(clock), margeMinimale);
        if (commande.montantTotal().valeur().signum() == 0) {
            // Une passerelle n'encaisse pas 0 F : la confirmation d'une commande gratuite
            // reste à concevoir (voir HANDOFF, section 7).
            throw new TransitionCommandeInterditeException("Une commande gratuite ne passe pas par le paiement");
        }

        PasserellePaiement.PaiementCree cree = passerelle.ouvrir(new PasserellePaiement.DemandePaiement(
            commande.getId(), moyen, commande.montantTotal(), commande.getTelephone(), "Billet+ : commande de billets"));

        try {
            Order enregistree = transaction.execute(statut -> {
                Order relue = orderRepository.findById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Commande introuvable"));
                relue.ouvrirPaiement(new Paiement(moyen, cree.reference(), cree.transactionId(), cree.url(),
                    Instant.now(clock)), margeMinimale);
                return orderRepository.save(relue);
            });
            return new LienPaiement(moyen, cree.url(), enregistree.getExpireLe());
        } catch (RuntimeException e) {
            LOG.warn("Paiement ouvert chez la passerelle mais non enregistré (commande {}, référence {}) : "
                + "il restera sans suite", orderId, cree.reference(), e);
            throw e;
        }
    }
}
