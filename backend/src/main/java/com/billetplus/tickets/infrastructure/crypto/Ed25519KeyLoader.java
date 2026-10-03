package com.billetplus.tickets.infrastructure.crypto;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Charge une clé Ed25519 depuis un fichier PEM standard (PKCS8 pour la privée,
 * X509 pour la publique) — format produit par
 * infra/scripts/generate-ed25519-keys.sh ou openssl directement.
 */
final class Ed25519KeyLoader {

    private Ed25519KeyLoader() {
    }

    static PrivateKey loadPrivateKey(Resource resource) {
        try {
            byte[] decoded = decodePem(resource);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Impossible de charger la clé privée Ed25519 depuis " + resource, e);
        }
    }

    static PublicKey loadPublicKey(Resource resource) {
        try {
            byte[] decoded = decodePem(resource);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Impossible de charger la clé publique Ed25519 depuis " + resource, e);
        }
    }

    private static byte[] decodePem(Resource resource) throws IOException {
        String content;
        try (InputStream is = resource.getInputStream()) {
            content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        String base64 = content
            .replaceAll("-----BEGIN (.*)-----", "")
            .replaceAll("-----END (.*)-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
