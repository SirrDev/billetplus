package com.billetplus.tickets.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTemporaireTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-29T10:00:00.123456789Z");
    private static final Duration CINQ_MINUTES = Duration.ofMinutes(5);

    private ReservationTemporaire reservation(int quantite) {
        return ReservationTemporaire.creer(UUID.randomUUID(), UUID.randomUUID(), quantite, MAINTENANT, CINQ_MINUTES);
    }

    @Test
    void expire_apres_la_duree_demandee_a_la_milliseconde() {
        ReservationTemporaire reservation = reservation(3);

        assertThat(reservation.getExpireLe()).isEqualTo(Instant.parse("2026-10-29T10:05:00.123Z"));
        assertThat(reservation.getQuantite()).isEqualTo(3);
    }

    @Test
    void est_valide_avant_son_expiration_et_expiree_a_partir_de_celle_ci() {
        ReservationTemporaire reservation = reservation(1);
        Instant expiration = reservation.getExpireLe();

        assertThat(reservation.estExpireeA(expiration.minusMillis(1))).isFalse();
        assertThat(reservation.estExpireeA(expiration)).isTrue();
        assertThat(reservation.estExpireeA(expiration.plusSeconds(60))).isTrue();
    }

    @Test
    void refuse_une_quantite_hors_des_bornes_d_une_commande() {
        assertThatThrownBy(() -> reservation(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation(QuantiteParCommande.MAX + 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accepte_exactement_le_plafond_d_une_commande() {
        assertThat(reservation(QuantiteParCommande.MAX).getQuantite()).isEqualTo(QuantiteParCommande.MAX);
    }

    @Test
    void refuse_une_duree_nulle_ou_negative() {
        UUID categoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> ReservationTemporaire.creer(categoryId, orderId, 1, MAINTENANT, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationTemporaire.creer(categoryId, orderId, 1, MAINTENANT, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_un_parametre_absent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> ReservationTemporaire.creer(null, id, 1, MAINTENANT, CINQ_MINUTES))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationTemporaire.creer(id, null, 1, MAINTENANT, CINQ_MINUTES))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationTemporaire.creer(id, id, 1, null, CINQ_MINUTES))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReservationTemporaire.creer(id, id, 1, MAINTENANT, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstituer_ne_revalide_pas_un_etat_deja_stocke() {
        UUID categoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant expireeDepuisLongtemps = Instant.parse("2020-01-01T00:00:00Z");

        ReservationTemporaire relue = ReservationTemporaire.reconstituer(categoryId, orderId, 2, expireeDepuisLongtemps);

        assertThat(relue.getCategoryId()).isEqualTo(categoryId);
        assertThat(relue.getOrderId()).isEqualTo(orderId);
        assertThat(relue.getQuantite()).isEqualTo(2);
        assertThat(relue.estExpireeA(MAINTENANT)).isTrue();
    }
}
