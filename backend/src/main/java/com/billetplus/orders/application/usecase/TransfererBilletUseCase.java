package com.billetplus.orders.application.usecase;

import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.tickets.api.publique.TransfertBillets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cas d'usage (ADR 0008) : l'acheteur transfère un billet de sa commande à un autre
 * mobile. C'est ici, et pas dans le module tickets, qu'est contrôlé son droit
 * (jeton de la commande, ADR 0006 ; contrat de TransfertBillets).
 *
 * Le jeton renvoyé est celui du destinataire : remis une seule fois à l'expéditeur,
 * qui le transmet lui-même (aucun SMS au MVP).
 */
@Service
public class TransfererBilletUseCase {

    private final ConsulterCommandeUseCase consulterCommande;
    private final TransfertBillets transfertBillets;

    public TransfererBilletUseCase(ConsulterCommandeUseCase consulterCommande, TransfertBillets transfertBillets) {
        this.consulterCommande = consulterCommande;
        this.transfertBillets = transfertBillets;
    }

    /**
     * @throws java.util.NoSuchElementException     (404) commande inconnue, jeton faux, ou billet d'une autre commande
     * @throws IllegalArgumentException             (400) mobile du destinataire invalide
     * @throws TransitionCommandeInterditeException (409) commande pas payée
     * @throws com.billetplus.shared.exception.BusinessConflictException (409) billet déjà transféré,
     *         plus valable, ou événement hors vente
     */
    @Transactional
    public String execute(UUID orderId, String jetonCommande, UUID ticketId, String telephoneSaisi) {
        Order commande = consulterCommande.execute(orderId, jetonCommande);
        if (commande.getStatus() != OrderStatus.PAYEE) {
            throw new TransitionCommandeInterditeException("Seul un billet d'une commande payée peut être transféré");
        }
        NumeroTelephone destinataire = NumeroTelephone.depuis(telephoneSaisi);
        return transfertBillets.transferer(commande.getId(), ticketId, destinataire.valeur());
    }
}
