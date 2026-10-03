package com.billetplus.tickets.infrastructure.crypto;

import com.billetplus.tickets.application.port.QRSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.security.PrivateKey;
import java.security.PublicKey;

@Configuration
public class Ed25519Config {

    @Bean
    public QRSigner qrSigner(
        @Value("${billetplus.security.qr-signing.private-key-path}") Resource privateKeyResource,
        @Value("${billetplus.security.qr-signing.public-key-path}") Resource publicKeyResource
    ) {
        PrivateKey privateKey = Ed25519KeyLoader.loadPrivateKey(privateKeyResource);
        PublicKey publicKey = Ed25519KeyLoader.loadPublicKey(publicKeyResource);
        return new Ed25519QRSigner(privateKey, publicKey);
    }
}
