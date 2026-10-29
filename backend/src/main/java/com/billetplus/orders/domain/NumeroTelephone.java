package com.billetplus.orders.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — numéro de mobile sénégalais de
 * l'acheteur, seule identité d'un achat sans compte (ADR 0006).
 *
 * Toujours conservé au format international (+2217XXXXXXXX, 13 caractères) :
 * « 77 123 45 67 », « +221 77 123 45 67 » et « 00221771234567 » désignent le même
 * acheteur, et les limites par téléphone ne doivent pas être contournées par une
 * autre écriture du même numéro.
 *
 * Seuls les mobiles (préfixe 7) sont acceptés : Wave fonctionne sur mobile. Le
 * numéro n'est pas vérifié par un code au MVP (ADR 0006).
 */
public final class NumeroTelephone {

    private static final Pattern MOBILE_LOCAL = Pattern.compile("7\\d{8}");
    /** Séparateurs tolérés à la saisie. */
    private static final Pattern SEPARATEURS = Pattern.compile("[\\s.\\-()]");
    private static final String INDICATIF = "+221";

    private final String valeur;

    private NumeroTelephone(String valeur) {
        this.valeur = valeur;
    }

    /**
     * @throws IllegalArgumentException si la saisie n'est pas un mobile sénégalais
     */
    public static NumeroTelephone depuis(String saisie) {
        if (saisie == null) {
            throw new IllegalArgumentException("Le numéro de téléphone est obligatoire");
        }
        String chiffres = SEPARATEURS.matcher(saisie.strip()).replaceAll("");
        if (chiffres.startsWith("+221")) {
            chiffres = chiffres.substring(4);
        } else if (chiffres.startsWith("00221")) {
            chiffres = chiffres.substring(5);
        } else if (chiffres.length() == 12 && chiffres.startsWith("221")) {
            chiffres = chiffres.substring(3);
        }
        if (!MOBILE_LOCAL.matcher(chiffres).matches()) {
            throw new IllegalArgumentException("Numéro de mobile sénégalais invalide");
        }
        return new NumeroTelephone(INDICATIF + chiffres);
    }

    /** Format international, par exemple +221771234567. */
    public String valeur() {
        return valeur;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumeroTelephone autre)) return false;
        return valeur.equals(autre.valeur);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valeur);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
