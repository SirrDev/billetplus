package com.billetplus.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void un_conflit_de_version_repond_409_sans_exposer_l_entite_ni_l_identifiant() {
        // Type réellement levé par Spring/Hibernate au flush (sous-classe d'OptimisticLockingFailureException).
        var exception = new ObjectOptimisticLockingFailureException("TicketCategoryJpaEntity", "id-interne-secret");

        ResponseEntity<Map<String, Object>> reponse = handler.handleOptimisticLock(exception);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reponse.getBody()).containsEntry("status", 409);
        assertThat((String) reponse.getBody().get("message"))
            .doesNotContain("TicketCategoryJpaEntity")
            .doesNotContain("id-interne-secret");
    }

    @Test
    void une_panne_redis_repond_503_sans_exposer_le_detail_de_connexion() {
        // Type réellement levé par Spring Data Redis quand le serveur est injoignable.
        var exception = new RedisConnectionFailureException("Unable to connect to redis-interne:6379");

        ResponseEntity<Map<String, Object>> reponse = handler.handleResourceFailure(exception);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(reponse.getBody()).containsEntry("status", 503);
        assertThat((String) reponse.getBody().get("message"))
            .doesNotContain("redis-interne")
            .doesNotContain("6379");
    }
}
