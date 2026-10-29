package com.billetplus.shared.limitation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Compteur Redis : INCR puis, au premier appel de la fenêtre, PEXPIRE. Les deux
 * dans un seul script, donc atomiques : sans cela, une panne entre les deux
 * laisserait un compteur sans expiration, qui bloquerait le demandeur pour
 * toujours. Aucun job de nettoyage : chaque clé expire avec sa fenêtre.
 *
 * @Component et non @Repository : pas de traduction d'exceptions implicite, comme
 * RedisSeatReservationStore.
 */
@Component
class RedisCompteurRequetes implements CompteurRequetes {

    /** KEYS[1] = compteur, ARGV[1] = durée de la fenêtre (ms). Renvoie le total de la fenêtre. */
    private static final RedisScript<Long> INCREMENTER = new DefaultRedisScript<>("""
        local total = redis.call('INCR', KEYS[1])
        if total == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return total
        """, Long.class);

    private final StringRedisTemplate redis;

    RedisCompteurRequetes(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long incrementer(String cle, Duration fenetre) {
        Long total = redis.execute(INCREMENTER, List.of(cle), Long.toString(fenetre.toMillis()));
        if (total == null) {
            throw new IllegalStateException("Réponse vide de Redis pour le compteur " + cle);
        }
        return total;
    }
}
