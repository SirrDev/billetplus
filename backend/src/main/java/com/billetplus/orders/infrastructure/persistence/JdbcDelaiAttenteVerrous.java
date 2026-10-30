package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.application.port.DelaiAttenteVerrous;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * SET LOCAL lock_timeout : ne vaut que pour la transaction courante, et disparaît
 * avec elle. Au-delà du délai, PostgreSQL lève une erreur 55P03, que Spring traduit
 * en PessimisticLockingFailureException (à relancer, jamais un « sans billet »).
 */
@Component
class JdbcDelaiAttenteVerrous implements DelaiAttenteVerrous {

    private final JdbcTemplate jdbc;

    JdbcDelaiAttenteVerrous(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appliquer(Duration delai) {
        // Valeur numérique en millisecondes : pas de concaténation d'une saisie.
        jdbc.execute("SET LOCAL lock_timeout = " + Math.max(1, delai.toMillis()));
    }
}
