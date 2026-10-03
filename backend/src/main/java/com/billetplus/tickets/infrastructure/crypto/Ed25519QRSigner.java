package com.billetplus.tickets.infrastructure.crypto;

import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.QrVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.*;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Implémentation Ed25519 pure — ne dépend d'aucun framework (pas de Spring ici),
 * testable directement avec une paire de clés générée en mémoire. Le wiring
 * Spring (chargement des clés depuis les fichiers PEM configurés) vit dans
 * Ed25519Config, séparé de cette classe.
 */
public class Ed25519QRSigner implements QRSigner {

    private static final String ALGORITHM = "Ed25519";
    private static final HexFormat HEX = HexFormat.of();

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final ObjectMapper objectMapper;

    public Ed25519QRSigner(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sign(UUID ticketId, UUID eventId, Instant issuedAt) {
        if (privateKey == null) {
            throw new IllegalStateException(
                "Aucune clé privée configurée — cette instance ne peut pas signer (scanner-only ?)");
        }
        try {
            String json = objectMapper.writeValueAsString(
                new QrPayload(ticketId.toString(), eventId.toString(), issuedAt.toEpochMilli()));

            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();

            return json + "." + HEX.formatHex(signatureBytes);
        } catch (GeneralSecurityException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Échec de la signature du QR code", e);
        }
    }

    @Override
    public QrVerificationResult verify(String qrContent) {
        String[] parts = qrContent.split("\\.", 2);
        if (parts.length != 2) {
            return QrVerificationResult.invalide();
        }
        String json = parts[0];
        String signatureHex = parts[1];

        try {
            byte[] signatureBytes = HEX.parseHex(signatureHex);

            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            if (!signature.verify(signatureBytes)) {
                return QrVerificationResult.invalide();
            }

            QrPayload payload = objectMapper.readValue(json, QrPayload.class);
            return QrVerificationResult.valide(
                UUID.fromString(payload.tid()),
                UUID.fromString(payload.eid()),
                Instant.ofEpochMilli(payload.iat())
            );
        } catch (Exception e) {
            // Volontairement large : tout contenu malformé, signature invalide,
            // hex corrompu ou JSON cassé aboutit au même résultat "invalide" —
            // ne jamais donner d'indice différencié qui aiderait à forger un QR.
            return QrVerificationResult.invalide();
        }
    }
}
