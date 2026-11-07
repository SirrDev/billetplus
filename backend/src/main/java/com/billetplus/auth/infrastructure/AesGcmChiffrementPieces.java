package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.ChiffrementPieces;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Chiffrement des pièces des organisateurs (ADR 0012) : AES-256-GCM, un nonce aléatoire de
 * 12 octets par fichier, placé devant le texte chiffré (et son étiquette d'authenticité de
 * 16 octets). La clé (32 octets, en Base64) vient de la configuration
 * ({@code BILLETPLUS_CLE_PIECES}) : jamais en base, jamais journalisée. Un contenu altéré
 * ou chiffré avec une autre clé est refusé au déchiffrement.
 */
@Component
class AesGcmChiffrementPieces implements ChiffrementPieces {

    /** Clé publique de développement (application.yml) : refusée en production (VerificationProduction). */
    static final String CLE_DE_DEV = "ZGV2LXVuaXF1ZW1lbnQtamFtYWlzLWVuLXByb2QtISE=";
    private static final int NONCE_OCTETS = 12;
    private static final int ETIQUETTE_BITS = 128;

    private final SecretKeySpec cle;
    private final SecureRandom aleatoire = new SecureRandom();

    AesGcmChiffrementPieces(@Value("${billetplus.verification.cle-pieces}") String cleBase64) {
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(cleBase64 == null ? "" : cleBase64.trim());
        } catch (IllegalArgumentException e) {
            // Sans recopier la valeur : c'est un secret.
            throw new IllegalStateException("billetplus.verification.cle-pieces n'est pas du Base64 valide");
        }
        if (octets.length != 32) {
            throw new IllegalStateException("billetplus.verification.cle-pieces doit faire 32 octets (AES-256)");
        }
        this.cle = new SecretKeySpec(octets, "AES");
    }

    @Override
    public byte[] chiffrer(byte[] clair) {
        byte[] nonce = new byte[NONCE_OCTETS];
        aleatoire.nextBytes(nonce);
        try {
            Cipher chiffreur = Cipher.getInstance("AES/GCM/NoPadding");
            chiffreur.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(ETIQUETTE_BITS, nonce));
            byte[] chiffre = chiffreur.doFinal(clair);
            byte[] resultat = Arrays.copyOf(nonce, NONCE_OCTETS + chiffre.length);
            System.arraycopy(chiffre, 0, resultat, NONCE_OCTETS, chiffre.length);
            return resultat;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement de la pièce impossible", e);
        }
    }

    @Override
    public byte[] dechiffrer(byte[] chiffre) {
        if (chiffre == null || chiffre.length <= NONCE_OCTETS) {
            throw new IllegalStateException("Pièce chiffrée illisible");
        }
        try {
            Cipher dechiffreur = Cipher.getInstance("AES/GCM/NoPadding");
            dechiffreur.init(Cipher.DECRYPT_MODE, cle,
                new GCMParameterSpec(ETIQUETTE_BITS, Arrays.copyOf(chiffre, NONCE_OCTETS)));
            return dechiffreur.doFinal(chiffre, NONCE_OCTETS, chiffre.length - NONCE_OCTETS);
        } catch (GeneralSecurityException e) {
            // Contenu altéré, ou chiffré avec une autre clé (clé perdue ou remplacée).
            throw new IllegalStateException("Pièce illisible : contenu altéré ou clé de chiffrement différente");
        }
    }
}
