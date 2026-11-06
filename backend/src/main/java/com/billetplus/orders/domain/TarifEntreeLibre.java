package com.billetplus.orders.domain;

import com.billetplus.shared.Money;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — ce que l'organisateur paie à Billet+ pour
 * chaque billet gratuit émis (ADR 0010) : {@code tarifPetitFcfa} si l'événement a
 * {@code seuilPlaces} places gratuites ou moins (les places payantes ne comptent pas), sinon
 * {@code tarifGrandFcfa}. Valeurs de la configuration, vérifiées au démarrage.
 *
 * @param seuilPlaces    nombre de places jusqu'auquel le petit tarif s'applique (inclus)
 * @param tarifPetitFcfa tarif d'un billet pour un événement de {@code seuilPlaces} places ou moins
 * @param tarifGrandFcfa tarif d'un billet au-delà
 */
public record TarifEntreeLibre(int seuilPlaces, int tarifPetitFcfa, int tarifGrandFcfa) {

    /** Décisions de SirrDev du 6 novembre 2026 : 75 F jusqu'à 100 places gratuites, 50 F au-delà. */
    public static final TarifEntreeLibre PAR_DEFAUT = new TarifEntreeLibre(100, 75, 50);

    public TarifEntreeLibre {
        if (seuilPlaces < 1) {
            throw new IllegalArgumentException("Le seuil de places de l'entrée libre doit être positif");
        }
        if (tarifPetitFcfa < 1 || tarifGrandFcfa < 1) {
            throw new IllegalArgumentException("Les tarifs de l'entrée libre doivent être positifs");
        }
    }

    /**
     * @param placesGratuites somme des quotas des catégories gratuites de l'événement
     * @return le tarif d'un billet gratuit pour cet événement, à ce moment
     */
    public Money tarifPour(long placesGratuites) {
        if (placesGratuites < 0) {
            throw new IllegalArgumentException("Le nombre de places ne peut pas être négatif");
        }
        return Money.ofFcfa(placesGratuites <= seuilPlaces ? tarifPetitFcfa : tarifGrandFcfa);
    }
}
