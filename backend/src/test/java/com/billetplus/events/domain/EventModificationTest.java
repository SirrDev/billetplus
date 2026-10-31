package com.billetplus.events.domain;

import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Event#modifier : ce qui change selon le statut de l'événement. */
class EventModificationTest {

    private static final Instant DEBUT = Instant.parse("2026-12-05T20:00:00Z");
    private static final Instant FIN = Instant.parse("2026-12-06T02:00:00Z");

    private Event brouillon() {
        return new Event("Concert", "Avant", DEBUT, FIN, new Venue("Sorano", "Dakar", null, null), UUID.randomUUID());
    }

    @Test
    void un_brouillon_change_titre_description_et_dates() {
        Event event = brouillon();
        Instant nouveauDebut = DEBUT.plus(7, ChronoUnit.DAYS);

        event.modifier("Grand concert", "Apres", nouveauDebut, null);

        assertThat(event.getTitre()).isEqualTo("Grand concert");
        assertThat(event.getDescription()).isEqualTo("Apres");
        assertThat(event.getDebuteLe()).isEqualTo(nouveauDebut);
        assertThat(event.getTermineLe()).isNull();
        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
    }

    @Test
    void un_evenement_publie_change_titre_et_description_si_les_dates_sont_renvoyees_inchangees() {
        Event event = brouillon();
        event.publier();

        event.modifier("Grand concert", null, DEBUT, FIN);

        assertThat(event.getTitre()).isEqualTo("Grand concert");
        assertThat(event.getDescription()).isNull();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLIE);
    }

    @Test
    void un_evenement_publie_refuse_un_changement_de_debut() {
        Event event = brouillon();
        event.publier();

        assertThatThrownBy(() -> event.modifier("Autre", "Avant", DEBUT.plus(1, ChronoUnit.HOURS), FIN))
            .isInstanceOf(BusinessConflictException.class);
        assertThat(event.getDebuteLe()).isEqualTo(DEBUT);
        assertThat(event.getTitre()).isEqualTo("Concert");
    }

    @Test
    void un_evenement_publie_refuse_de_retirer_sa_fin() {
        Event event = brouillon();
        event.publier();

        assertThatThrownBy(() -> event.modifier("Concert", "Avant", DEBUT, null))
            .isInstanceOf(BusinessConflictException.class);
        assertThat(event.getTermineLe()).isEqualTo(FIN);
    }

    @Test
    void un_evenement_annule_n_est_plus_modifiable() {
        Event event = brouillon();
        event.annuler();

        assertThatThrownBy(() -> event.modifier("Autre", "Avant", DEBUT, FIN))
            .isInstanceOf(BusinessConflictException.class);
        assertThat(event.getTitre()).isEqualTo("Concert");
    }

    @Test
    void refuse_un_titre_vide_une_fin_avant_le_debut_ou_un_debut_absent_sans_rien_changer() {
        Event event = brouillon();

        assertThatThrownBy(() -> event.modifier(" ", "Avant", DEBUT, FIN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event.modifier("Concert", "Avant", DEBUT, DEBUT.minus(1, ChronoUnit.MINUTES)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event.modifier("Concert", "Avant", null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.getTermineLe()).isEqualTo(FIN);
    }
}
