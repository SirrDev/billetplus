package com.billetplus.shared.limitation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Branche l'intercepteur de limitation sur /api/**. Dans shared : mécanisme
 * purement technique, transverse à tous les modules, sans règle métier (ADR 0001).
 * Les règles elles-mêmes sont dans application.yml.
 */
@Configuration
@EnableConfigurationProperties(LimitationDebitProperties.class)
class LimitationDebitConfig implements WebMvcConfigurer {

    private final LimitationDebitProperties proprietes;
    private final CompteurRequetes compteur;
    private final Clock clock;

    LimitationDebitConfig(LimitationDebitProperties proprietes, CompteurRequetes compteur, Clock clock) {
        this.proprietes = proprietes;
        this.compteur = compteur;
        this.clock = clock;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (proprietes.active()) {
            registry.addInterceptor(new LimitationDebitInterceptor(proprietes, compteur, clock))
                .addPathPatterns("/api/**");
        }
    }
}
