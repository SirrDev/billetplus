package com.billetplus.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Profil prod uniquement : refuse de démarrer avec une configuration dangereuse,
 * plutôt que de la découvrir le jour de l'événement.
 *
 * Ne lit que des propriétés et Redis, jamais une classe d'un autre module :
 * contrôle technique transverse, à sa place dans shared (ADR 0001). Les valeurs
 * vérifiées ne sont jamais journalisées.
 *
 * Redis : une politique d'éviction autre que noeviction supprimerait des
 * réservations de places sans prévenir quand la mémoire est pleine (client payé
 * sans billet). Si Redis refuse CONFIG GET (service géré), le démarrage continue
 * avec un WARN : la politique est alors à vérifier dans la console du fournisseur.
 */
@Component
@Profile("prod")
class VerificationProduction {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationProduction.class);
    /** Valeur par défaut d'application.yml, publique : jamais acceptable en production. */
    static final String SECRET_JWT_DE_DEV = "changeme_dev_only_never_use_in_prod";
    static final int SECRET_JWT_OCTETS_MIN = 32;

    VerificationProduction(@Value("${billetplus.security.jwt.secret}") String secretJwt,
                           @Value("${billetplus.security.qr-signing.private-key-path}") String clePrivee,
                           @Value("${billetplus.security.qr-signing.public-key-path}") String clePublique,
                           @Value("${billetplus.limitation.active:true}") boolean limitationActive,
                           @Value("${billetplus.paiement.unitechpay.cle-api:}") String clePaiement,
                           @Value("${billetplus.verification.cle-pieces:}") String clePieces,
                           StringRedisTemplate redis) {
        List<String> erreurs = new ArrayList<>(verifier(secretJwt, clePrivee, clePublique, limitationActive, clePaiement));
        erreurs.addAll(verifierClePieces(clePieces));
        if (!erreurs.isEmpty()) {
            throw new IllegalStateException("Configuration de production refusée : " + String.join(" ; ", erreurs));
        }
        verifierPolitiqueRedis(redis);
    }

    static List<String> verifier(String secretJwt, String clePrivee, String clePublique, boolean limitationActive,
                                 String clePaiement) {
        List<String> erreurs = new ArrayList<>();
        if (secretJwt == null || secretJwt.equals(SECRET_JWT_DE_DEV)
            || secretJwt.getBytes(StandardCharsets.UTF_8).length < SECRET_JWT_OCTETS_MIN) {
            erreurs.add("JWT_SECRET doit être un secret propre à la production, d'au moins "
                + SECRET_JWT_OCTETS_MIN + " octets");
        }
        // Le classpath ne contient que la paire de dev : en production, les deux clés
        // viennent de fichiers montés hors de l'image.
        if (clePrivee == null || clePrivee.startsWith("classpath:")) {
            erreurs.add("QR_PRIVATE_KEY_PATH doit désigner un fichier hors de l'image (pas classpath:)");
        }
        if (clePublique == null || clePublique.startsWith("classpath:")) {
            erreurs.add("QR_PUBLIC_KEY_PATH doit désigner un fichier hors de l'image (pas classpath:)");
        }
        if (!limitationActive) {
            erreurs.add("billetplus.limitation.active ne peut pas valoir false en production");
        }
        if (clePaiement == null || clePaiement.isBlank()) {
            erreurs.add("UNITECHPAY_API_KEY est obligatoire en production (paiement et signature des webhooks)");
        }
        return erreurs;
    }

    /** Clé de dev d'application.yml, publique (ADR 0012). */
    static final String CLE_PIECES_DE_DEV = "ZGV2LXVuaXF1ZW1lbnQtamFtYWlzLWVuLXByb2QtISE=";

    /** La clé des pièces doit être propre à la production (son format est vérifié à part). */
    static List<String> verifierClePieces(String clePieces) {
        if (clePieces == null || clePieces.isBlank() || clePieces.trim().equals(CLE_PIECES_DE_DEV)) {
            return List.of("BILLETPLUS_CLE_PIECES doit être une clé propre à la production (32 octets en Base64)");
        }
        return List.of();
    }

    private static void verifierPolitiqueRedis(StringRedisTemplate redis) {
        String politique;
        try {
            Properties config = redis.execute((RedisCallback<Properties>) connexion ->
                connexion.serverCommands().getConfig("maxmemory-policy"));
            politique = config == null ? null : config.getProperty("maxmemory-policy");
        } catch (DataAccessException e) {
            LOG.warn("Politique d'éviction de Redis illisible (CONFIG GET refusé ou Redis injoignable) : "
                + "vérifier qu'elle vaut noeviction", e);
            return;
        }
        if (politique == null) {
            LOG.warn("Politique d'éviction de Redis absente de la réponse : vérifier qu'elle vaut noeviction");
            return;
        }
        if (!"noeviction".equals(politique)) {
            throw new IllegalStateException("Redis doit être configuré avec maxmemory-policy noeviction (actuellement "
                + politique + ") : une éviction supprimerait des réservations de places sans prévenir");
        }
    }
}
