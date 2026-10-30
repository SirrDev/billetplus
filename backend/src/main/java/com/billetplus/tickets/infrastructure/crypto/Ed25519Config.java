package com.billetplus.tickets.infrastructure.crypto;

import com.billetplus.tickets.application.port.QRSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

@Configuration
public class Ed25519Config {

    @Bean
    public QRSigner qrSigner(
        @Value("${billetplus.security.qr-signing.private-key-path}") Resource privateKeyResource,
        @Value("${billetplus.security.qr-signing.public-key-path}") Resource publicKeyResource
    ) {
        PrivateKey privateKey = Ed25519KeyLoader.loadPrivateKey(privateKeyResource);
        PublicKey publicKey = Ed25519KeyLoader.loadPublicKey(publicKeyResource);
        verifierLaPaire(privateKey, publicKey);
        return new Ed25519QRSigner(privateKey, publicKey);
    }

    /**
     * Refuse de démarrer si la clé publique n'est pas celle de la clé privée.
     * Sans ce contrôle, une clé privée de production associée à la clé publique
     * de dev (ou l'inverse) signerait des billets que le contrôle d'entrée
     * refuserait tous, le jour même de l'événement.
     *
     * @throws IllegalStateException si la signature d'un message de test ne se vérifie pas
     */
    static void verifierLaPaire(PrivateKey privateKey, PublicKey publicKey) {
        byte[] message = "billetplus-verification-paire-ed25519".getBytes(StandardCharsets.UTF_8);
        try {
            Signature signataire = Signature.getInstance("Ed25519");
            signataire.initSign(privateKey);
            signataire.update(message);
            byte[] signature = signataire.sign();

            Signature verificateur = Signature.getInstance("Ed25519");
            verificateur.initVerify(publicKey);
            verificateur.update(message);
            if (!verificateur.verify(signature)) {
                throw new IllegalStateException("La clé publique Ed25519 ne correspond pas à la clé privée : "
                    + "vérifier QR_PRIVATE_KEY_PATH et QR_PUBLIC_KEY_PATH");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Vérification de la paire de clés Ed25519 impossible", e);
        }
    }
}
