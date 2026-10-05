package com.billetplus.events.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private Venue venue() {
        return new Venue("Grand Théâtre", "Dakar", null, null);
    }

    @Test
    void cree_un_evenement_en_brouillon_par_defaut() {
        Event event = new Event("Concert", "Description", Instant.now(), null, venue(), UUID.randomUUID());

        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
        assertThat(event.estPublie()).isFalse();
    }

    @Test
    void refuse_une_date_de_fin_anterieure_a_la_date_de_debut() {
        Instant debut = Instant.now();
        Instant fin = debut.minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> new Event("Concert", null, debut, fin, venue(), UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_un_titre_vide() {
        assertThatThrownBy(() -> new Event("", null, Instant.now(), null, venue(), UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publier_change_le_statut_vers_publie() {
        Event event = new Event("Concert", null, Instant.now(), null, venue(), UUID.randomUUID());

        event.publier();

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLIE);
        assertThat(event.estPublie()).isTrue();
    }

    @Test
    void refuse_de_publier_un_evenement_deja_publie() {
        Event event = new Event("Concert", null, Instant.now(), null, venue(), UUID.randomUUID());
        event.publier();

        assertThatThrownBy(event::publier)
            .isInstanceOf(IllegalStateException.class);
    }

    // Pas de test sur "refuse d'annuler un événement TERMINE" : aucun mécanisme
    // ne permet encore d'atteindre ce statut (pas de setter, pas de transition
    // automatique). À ajouter dès qu'un tel mécanisme existe (ex. job planifié
    // en V2) — un test sur un état inatteignable passerait sans rien vérifier.
}
