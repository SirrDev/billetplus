package com.billetplus.tickets.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Domaine pur (niveau 3, voir ADR 0002 et ADR 0008) : secret d'accès à un billet
 * transféré. Même construction que le jeton de commande (orders.domain.JetonAcces,
 * ADR 0006), recopiée ici : un module ne dépend pas du domaine d'un autre.
 *
 * 256 bits aléatoires, encodés en Base64 URL (43 caractères), remis une seule
 * fois à l'expéditeur, qui le transmet au destinataire. Seule l'empreinte SHA-256
 * (64 caractères hexadécimaux) est conservée : une fuite de la base n'ouvre aucun
 * billet.
 *
 * Uniquement des classes du JDK : le domaine reste un POJO pur.
 */
public final class JetonTransfert {

    private static final int OCTETS = 32;
    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private final String enClair;
    private final String empreinte;

    private JetonTransfert(String enClair) {
        this.enClair = enClair;
        this.empreinte = empreinteDe(enClair);
    }

    public static JetonTransfert generer() {
        byte[] octets = new byte[OCTETS];
        ALEATOIRE.nextBytes(octets);
        return new JetonTransfert(Base64.getUrlEncoder().withoutPadding().encodeToString(octets));
    }

    /** Empreinte SHA-256, en hexadécimal minuscule. */
    public static String empreinteDe(String jeton) {
        return HexFormat.of().formatHex(sha256(jeton));
    }

    /**
     * Compare un jeton présenté à une empreinte stockée, en temps constant
     * (MessageDigest.isEqual) : la durée de la comparaison ne révèle rien.
     * Faux pour un jeton absent ou une empreinte mal formée.
     */
    public static boolean correspond(String jetonPresente, String empreinteStockee) {
        if (jetonPresente == null || empreinteStockee == null || empreinteStockee.length() != OCTETS * 2) {
            return false;
        }
        byte[] attendue;
        try {
            attendue = HexFormat.of().parseHex(empreinteStockee);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(sha256(jetonPresente), attendue);
    }

    private static byte[] sha256(String texte) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est obligatoire dans toute JVM conforme : ne peut pas arriver.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** À remettre à l'expéditeur, puis à oublier : jamais stocké ni journalisé. */
    public String enClair() {
        return enClair;
    }

    public String empreinte() {
        return empreinte;
    }

    /** Volontairement sans le jeton : un toString() journalisé ne doit pas le divulguer. */
    @Override
    public String toString() {
        return "JetonTransfert[masqué]";
    }
}
