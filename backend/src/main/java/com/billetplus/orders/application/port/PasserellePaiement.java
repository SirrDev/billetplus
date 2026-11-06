package com.billetplus.orders.application.port;

import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.shared.Money;

import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — ouverture d'un paiement chez la passerelle
 * (UnitechPay, ADR 0007). L'adaptateur HTTP vit dans infrastructure/paiement/ :
 * ni le domaine ni les cas d'usage ne connaissent l'URL, les champs ou la clé.
 *
 * Appel réseau : jamais dans une transaction PostgreSQL ouverte (ADR 0007).
 */
public interface PasserellePaiement {

    /** Page de la PWA où revient le payeur : sa commande, ou le crédit de l'organisateur (ADR 0010). */
    enum Retour {
        COMMANDE,
        RECHARGE
    }

    /**
     * @param orderId     identifiant de la commande (ou de la recharge), pour les liens de retour vers la PWA
     * @param description texte affiché au payeur par la passerelle (sans donnée personnelle)
     */
    record DemandePaiement(UUID orderId, MoyenPaiement moyen, Money montant, NumeroTelephone telephone,
                           String description, Retour retour) {

        /** Paiement d'une commande de billets. */
        public DemandePaiement(UUID orderId, MoyenPaiement moyen, Money montant, NumeroTelephone telephone,
                               String description) {
            this(orderId, moyen, montant, telephone, description, Retour.COMMANDE);
        }
    }

    /** @param reference référence générée par la passerelle, clé de rapprochement du webhook */
    record PaiementCree(String reference, long transactionId, String url) {
    }

    /**
     * @throws com.billetplus.shared.exception.ServiceExterneIndisponibleException (503) passerelle
     *         injoignable, en erreur, mal configurée ou réponse inexploitable
     * @throws com.billetplus.orders.domain.exception.PaiementRefuseException (409) demande refusée
     *         par la passerelle (numéro ou moyen de paiement non accepté, par exemple)
     */
    PaiementCree ouvrir(DemandePaiement demande);
}
