package com.billetplus.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Horloge de l'application, injectée plutôt qu'appelée en dur
 * (Instant.now(clock)) : les tests peuvent fixer le temps. C'est aussi la
 * seule source d'heure des réservations temporaires (voir
 * RedisSeatReservationStore).
 *
 * Dans shared et non dans un module : le bean Clock est unique dans le
 * contexte et servira aussi à orders. Type purement technique, sans logique
 * métier (ADR 0001).
 */
@Configuration
class HorlogeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
