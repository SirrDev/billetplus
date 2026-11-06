package com.billetplus.orders.domain;

import com.billetplus.shared.Money;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — ce que l'organisateur paie à Billet+ pour
 * chaque billet gratuit émis (ADR 0010) : {@code tarifPetitFcfa} si l'événement a
 * {@code seuilPlaces} places ou moins, toutes catégories confondues, sinon
 * {@code tarifGrandFcfa}. Valeurs de la configuration, vérifiées au démarrage.
 *
 * @param seuilPlaces    nombre de places jusqu'auquel le petit tarif s'applique (inclus)
 * @param tarifPetitFcfa tarif d'un billet pour un événement de {@code seuilPlaces} places ou moins
 * @param tarifGrandFcfa tarif d'un billet au-delà
 */
public record TarifEntreeLibre(int seuilPlaces, int tarifPetitFcfa, int tarifGrandFcfa) {

    /** Décision de SirrDev du 6 novembre 2026 : 75 F jusqu'à 100 places, 50 F au-delà. */
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
     * @param placesTotales somme des quotas de toutes les catégories de l'événement
     * @return le tarif d'un billet gratuit pour cet événement, à ce moment
     */
    public Money tarifPour(long placesTotales) {
        if (placesTotales < 0) {
            throw new IllegalArgumentException("Le nombre de places ne peut pas être négatif");
        }
        return Money.ofFcfa(placesTotales <= seuilPlaces ? tarifPetitFcfa : tarifGrandFcfa);
    }
}
