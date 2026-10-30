package com.billetplus.orders.application.usecase;

import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.tickets.api.publique.BilletVendu;
import com.billetplus.tickets.api.publique.BilletsDeCommande;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage — les billets d'une commande, pour son acheteur (ADR 0006 : le
 * jeton d'accès fait office d'identité). C'est ici, et pas dans le module
 * tickets, qu'est contrôlé le droit de les voir (contrat de BilletsDeCommande).
 */
@Service
public class ConsulterBilletsUseCase {

    private final ConsulterCommandeUseCase consulterCommande;
    private final BilletsDeCommande billetsDeCommande;

    public ConsulterBilletsUseCase(ConsulterCommandeUseCase consulterCommande, BilletsDeCommande billetsDeCommande) {
        this.consulterCommande = consulterCommande;
        this.billetsDeCommande = billetsDeCommande;
    }

    /**
     * @throws NoSuchElementException               (404) commande inconnue ou jeton faux
     * @throws TransitionCommandeInterditeException (409) commande pas (ou pas encore) payée
     */
    public List<BilletVendu> execute(UUID orderId, String jeton) {
        Order commande = consulterCommande.execute(orderId, jeton);
        if (commande.getStatus() != OrderStatus.PAYEE) {
            // En attente : le webhook n'est peut-être pas encore arrivé, la PWA réessaie.
            throw new TransitionCommandeInterditeException(
                "Les billets ne sont disponibles qu'une fois la commande payée");
        }
        return billetsDeCommande.billetsDe(commande.getId());
    }
}
