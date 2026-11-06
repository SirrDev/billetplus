package com.billetplus.orders.domain;

import com.billetplus.shared.Money;

/**
 * Recettes d'un événement et part de l'organisateur (ADR 0009).
 *
 * Seules les commandes PAYEE comptent, et seulement si l'événement n'est pas annulé :
 * une commande PAYEE_SANS_BILLET, ou toute commande d'un événement annulé, est remboursée
 * en entier à l'acheteur (ni commission ni net). Les totaux viennent de la base, qui
 * arrondit la commission commande par commande (OrderRepository.TotauxPayees).
 *
 * @param reverseFcfa         total des reversements enregistrés (signé : corrections comprises)
 * @param resteAReverserFcfa  net moins déjà reversé ; négatif si l'organisateur a trop reçu
 *                            (par exemple événement annulé après un reversement)
 */
public record RecettesEvenement(long commandes, Money brut, Money commission, Money net,
                                long reverseFcfa, long resteAReverserFcfa) {

    /**
     * @throws IllegalArgumentException si les totaux sont incohérents (commission supérieure
     *                                  au brut, valeurs négatives) : défaut de calcul, à ne pas masquer
     */
    public static RecettesEvenement depuisTotaux(long commandes, long brutFcfa, long commissionFcfa,
                                                 boolean evenementAnnule, long reverseFcfa) {
        if (commandes < 0 || brutFcfa < 0 || commissionFcfa < 0 || commissionFcfa > brutFcfa) {
            throw new IllegalArgumentException("Totaux de commandes incohérents");
        }
        Money brut = evenementAnnule ? Money.ofFcfa(0) : Money.ofFcfa(brutFcfa);
        Money commission = evenementAnnule ? Money.ofFcfa(0) : Money.ofFcfa(commissionFcfa);
        Money net = brut.moins(commission);
        long reste = Math.subtractExact(net.valeur().longValueExact(), reverseFcfa);
        return new RecettesEvenement(evenementAnnule ? 0 : commandes, brut, commission, net, reverseFcfa, reste);
    }
}
