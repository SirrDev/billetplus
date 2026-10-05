package com.billetplus.events.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueTest {

    @Test
    void cree_un_lieu_valide() {
        Venue venue = new Venue("Grand Théâtre de Dakar", "Avenue Léopold Sédar Senghor", 14.6928, -17.4467);

        assertThat(venue.getNom()).isEqualTo("Grand Théâtre de Dakar");
        assertThat(venue.getSeatingPlan()).isNull();
    }

    @Test
    void refuse_un_nom_vide() {
        assertThatThrownBy(() -> new Venue("", "Adresse", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_une_adresse_vide() {
        assertThatThrownBy(() -> new Venue("Nom", "", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permet_de_definir_le_plan_de_salle_apres_creation() {
        Venue venue = new Venue("Stade", "Adresse", null, null);

        venue.definirPlanDeSalle("{\"sections\":[]}");

        assertThat(venue.getSeatingPlan()).isEqualTo("{\"sections\":[]}");
    }
}
