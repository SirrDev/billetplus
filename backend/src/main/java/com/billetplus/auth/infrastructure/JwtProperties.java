package com.billetplus.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lie billetplus.security.jwt.* depuis application.yml.
 * Le secret de dev dans application.yml est volontairement faible
 * ("changeme_dev_only_never_use_in_prod") — en production, il est injecté
 * via la variable d'environnement JWT_SECRET (secret manager), jamais commité.
 */
@ConfigurationProperties(prefix = "billetplus.security.jwt")
public class JwtProperties {

    private String secret;
    private int expirationMinutes = 15;
    private int refreshExpirationDays = 7;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(int expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }

    public int getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    public void setRefreshExpirationDays(int refreshExpirationDays) {
        this.refreshExpirationDays = refreshExpirationDays;
    }
}