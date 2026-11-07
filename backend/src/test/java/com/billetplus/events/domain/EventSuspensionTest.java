package com.billetplus.events.domain;

import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSuspensionTest {

    private Event brouillon() {
        Venue venue = new Venue("Grand Theatre", "Dakar", null, null, VillesDeTest.dakar());
        return new Event("Concert", null, Instant.parse("2026-12-01T20:00:00Z"), null, venue, UUID.randomUUID());
    }

    @Test
    void suspendre_un_brouillon_l_annule_avec_le_motif() {
        Event event = brouillon();

        event.suspendre("  Compte frauduleux  ");

        assertThat(event.getStatus()).isEqualTo(EventStatus.ANNULE);
        assertThat(event.getSuspenduMotif()).isEqualTo("Compte frauduleux");
        assertThat(event.estActif()).isFalse();
    }

    @Test
    void suspendre_un_evenement_publie_l_annule() {
        Event event = brouillon();
        event.publier();

        event.suspendre("Faux evenement");

        assertThat(event.getStatus()).isEqualTo(EventStatus.ANNULE);
    }

    @Test
    void suspendre_sans_motif_est_refuse_et_ne_change_pas_le_statut() {
        Event event = brouillon();

        assertThatThrownBy(() -> event.suspendre("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event.suspendre(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
        assertThat(event.getSuspenduMotif()).isNull();
    }

    @Test
    void suspendre_avec_un_motif_de_501_caracteres_est_refuse() {
        Event event = brouillon();

        assertThatThrownBy(() -> event.suspendre("m".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
    }

    @Test
    void suspendre_un_evenement_deja_annule_est_un_conflit_et_garde_le_premier_motif() {
        Event event = brouillon();
        event.annuler();

        assertThatThrownBy(() -> event.suspendre("Motif")).isInstanceOf(BusinessConflictException.class);

        assertThat(event.getSuspenduMotif()).isNull();
    }

    @Test
    void suspendre_deux_fois_est_un_conflit() {
        Event event = brouillon();
        event.suspendre("Premier motif");

        assertThatThrownBy(() -> event.suspendre("Second motif")).isInstanceOf(BusinessConflictException.class);

        assertThat(event.getSuspenduMotif()).isEqualTo("Premier motif");
    }

    @Test
    void un_evenement_annule_n_est_pas_actif() {
        Event event = brouillon();
        assertThat(event.estActif()).isTrue();
        event.publier();
        assertThat(event.estActif()).isTrue();
        event.annuler();

        assertThat(event.estActif()).isFalse();
    }
}
