package com.billetplus.tickets.infrastructure.redis;

import com.billetplus.tickets.application.port.SeatReservationStore;
import com.billetplus.tickets.domain.ReservationTemporaire;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur Redis du port SeatReservationStore (ADR 0003 : réservation
 * temporaire du panier, sans job de nettoyage).
 *
 * Trois clés par catégorie :
 * - tickets:reservations:{categoryId}            sorted set, membre = orderId, score = expiration (epoch ms) ;
 * - tickets:reservations:{categoryId}:quantites  hash, orderId → quantité ;
 * - tickets:reservations:{categoryId}:total      somme des quantités du hash, tenue à jour par les scripts.
 * Le total évite de parcourir toutes les réservations à chaque appel : Redis
 * n'exécute qu'un script à la fois, et une somme en O(n) sur une grande
 * catégorie ralentirait toute l'application. Le hash tag {categoryId} place les
 * trois clés sur le même nœud si Redis passe un jour en cluster.
 *
 * Les réservations expirées sont retirées à chaque réservation (nettoyage
 * paresseux, au plus {@value #NETTOYAGE_MAX_PAR_APPEL} par appel pour borner la
 * durée du script). Tant qu'une réservation expirée n'est pas retirée, elle
 * reste comptée : l'erreur est prudente (place refusée à tort), jamais une
 * survente. Les trois clés portent une expiration Redis calée sur la
 * réservation la plus lointaine : une catégorie inactive ne laisse rien.
 *
 * L'heure vient de l'application ({@code maintenant}), pas du serveur Redis :
 * une seule source d'heure. En multi-instances, les horloges doivent être
 * synchronisées (NTP).
 *
 * @Component et non @Repository, comme JpaTicketIssuanceRepositoryAdapter : pas
 * de traduction d'exceptions implicite. Une panne Redis remonte en
 * RedisConnectionFailureException (DataAccessResourceFailureException → 503).
 */
@Component
class RedisSeatReservationStore implements SeatReservationStore {

    /** Marge ajoutée à l'expiration Redis des clés, au-delà de la réservation la plus lointaine. */
    private static final long MARGE_EXPIRATION_CLES_MS = 60_000;

    /** Nombre maximal de réservations expirées retirées par appel. */
    static final int NETTOYAGE_MAX_PAR_APPEL = 100;

    /**
     * KEYS[1] = sorted set, KEYS[2] = hash des quantités, KEYS[3] = total.
     * ARGV[1] = orderId, ARGV[2] = quantité, ARGV[3] = expiration demandée (ms),
     * ARGV[4] = maintenant (ms), ARGV[5] = limite, ARGV[6] = marge d'expiration
     * des clés (ms), ARGV[7] = nettoyage maximal par appel.
     * Renvoie l'expiration enregistrée (ms), ou -1 si la réservation dépasserait la limite.
     */
    private static final RedisScript<Long> RESERVER = new DefaultRedisScript<>("""
        local function retirer(membre)
            local quantite = redis.call('HGET', KEYS[2], membre)
            if quantite then
                redis.call('DECRBY', KEYS[3], quantite)
                redis.call('HDEL', KEYS[2], membre)
            end
            redis.call('ZREM', KEYS[1], membre)
        end

        local maintenant = tonumber(ARGV[4])
        local expirees = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', maintenant, 'LIMIT', 0, tonumber(ARGV[7]))
        for _, membre in ipairs(expirees) do
            retirer(membre)
        end

        -- Réservation actuelle de la commande : expirée (au-delà du nettoyage borné), elle est retirée.
        local scoreActuel = redis.call('ZSCORE', KEYS[1], ARGV[1])
        if scoreActuel and tonumber(scoreActuel) <= maintenant then
            retirer(ARGV[1])
            scoreActuel = false
        end
        local actuelle = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')

        local quantite = tonumber(ARGV[2])
        local autres = tonumber(redis.call('GET', KEYS[3]) or '0') - actuelle
        if autres + quantite > tonumber(ARGV[5]) then
            return -1
        end

        -- Remplacement : la quantité change, l'expiration d'origine est conservée.
        local expiration = scoreActuel and tonumber(scoreActuel) or tonumber(ARGV[3])
        redis.call('ZADD', KEYS[1], expiration, ARGV[1])
        redis.call('HSET', KEYS[2], ARGV[1], quantite)
        redis.call('INCRBY', KEYS[3], quantite - actuelle)

        local plusLointaine = tonumber(redis.call('ZRANGE', KEYS[1], -1, -1, 'WITHSCORES')[2])
        local expirationCles = plusLointaine + tonumber(ARGV[6])
        for i = 1, 3 do
            redis.call('PEXPIREAT', KEYS[i], expirationCles)
        end
        return expiration
        """, Long.class);

    /** KEYS[1] = sorted set, KEYS[2] = hash des quantités, KEYS[3] = total ; ARGV[1] = orderId. */
    private static final RedisScript<Long> LIBERER = new DefaultRedisScript<>("""
        local quantite = redis.call('HGET', KEYS[2], ARGV[1])
        if quantite then
            redis.call('DECRBY', KEYS[3], quantite)
            redis.call('HDEL', KEYS[2], ARGV[1])
        end
        redis.call('ZREM', KEYS[1], ARGV[1])
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;

    RedisSeatReservationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<Instant> reserver(ReservationTemporaire reservation, int limite, Instant maintenant) {
        Long expiration = redis.execute(RESERVER, cles(reservation.getCategoryId()),
            reservation.getOrderId().toString(),
            String.valueOf(reservation.getQuantite()),
            String.valueOf(reservation.getExpireLe().toEpochMilli()),
            String.valueOf(maintenant.toEpochMilli()),
            String.valueOf(limite),
            String.valueOf(MARGE_EXPIRATION_CLES_MS),
            String.valueOf(NETTOYAGE_MAX_PAR_APPEL));
        if (expiration == null || expiration < 0) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(expiration));
    }

    @Override
    public void liberer(UUID categoryId, UUID orderId) {
        redis.execute(LIBERER, cles(categoryId), orderId.toString());
    }

    static String cleReservations(UUID categoryId) {
        return "tickets:reservations:{" + categoryId + "}";
    }

    static String cleQuantites(UUID categoryId) {
        return cleReservations(categoryId) + ":quantites";
    }

    static String cleTotal(UUID categoryId) {
        return cleReservations(categoryId) + ":total";
    }

    private static List<String> cles(UUID categoryId) {
        return List.of(cleReservations(categoryId), cleQuantites(categoryId), cleTotal(categoryId));
    }
}
