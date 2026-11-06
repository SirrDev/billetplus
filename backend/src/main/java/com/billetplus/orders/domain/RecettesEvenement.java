package com.billetplus.orders.domain;

import com.billetplus.shared.Money;

import java.util.List;

/**
 * Recettes d'un événement et part de l'organisateur (ADR 0009).
 *
 * Seules les commandes PAYEE comptent, et seulement si l'événement n'est pas annulé :
 * une commande PAYEE_SANS_BILLET, ou toute commande d'un événement annulé, est remboursée
 * en entier à l'acheteur (ni commission ni net). La commission est calculée commande par
 * commande, avec le taux figé dans chacune.
 *
 * @param reverseFcfa         total des reversements enregistrés (signé : corrections comprises)
 * @param resteAReverserFcfa  net moins déjà reversé ; négatif si l'organisateur a trop reçu
 *                            (par exemple événement annulé après un reversement)
 */
public record RecettesEvenement(int commandes, Money brut, Money commission, Money net,
                                long reverseFcfa, long resteAReverserFcfa) {

    public static RecettesEvenement calculer(List<Order> commandes, boolean evenementAnnule, long reverseFcfa) {
        Money brut = Money.ofFcfa(0);
        Money commission = Money.ofFcfa(0);
        int nombre = 0;
        if (!evenementAnnule) {
            for (Order commande : commandes) {
                if (commande.getStatus() == OrderStatus.PAYEE) {
                    brut = brut.plus(commande.montantTotal());
                    commission = commission.plus(commande.commission());
                    nombre++;
                }
            }
        }
        Money net = brut.moins(commission);
        long reste = Math.subtractExact(net.valeur().longValueExact(), reverseFcfa);
        return new RecettesEvenement(nombre, brut, commission, net, reverseFcfa, reste);
    }
}
