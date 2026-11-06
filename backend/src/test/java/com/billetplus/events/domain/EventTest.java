package com.billetplus.events.domain;

import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private Venue venue() {
        return new Venue("Grand Théâtre", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.dakar());
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

    /** ADR 0011, point 9 : la vente n'est ouverte qu'au Sénégal pour l'instant. */
    @Test
    void un_evenement_hors_du_senegal_ne_se_publie_pas_et_reste_brouillon() {
        Venue abidjan = new Venue("Palais de la culture", "Treichville", null, null,
            new Ville("CI-ABIDJAN", "Abidjan", Pays.CI, 1));
        Event event = new Event("Concert", null, Instant.now(), null, abidjan, UUID.randomUUID());

        assertThatThrownBy(event::publier)
            .isInstanceOf(BusinessConflictException.class)
            .hasMessage("La vente hors du Sénégal n'est pas encore ouverte");
        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
    }

    @Test
    void une_copie_du_lieu_garde_sa_ville() {
        Venue thies = new Venue("Stade", "Route de Dakar", null, null, new Ville("SN-THIES", "Thiès", Pays.SN, 2));

        assertThat(thies.copieA(14.79, -16.93).getVille().getCode()).isEqualTo("SN-THIES");
    }

    @Test
    void refuse_de_publier_un_evenement_deja_publie() {
        Event event = new Event("Concert", null, Instant.now(), null, venue(), UUID.randomUUID());
        event.publier();

        assertThatThrownBy(event::publier)
            .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void refuse_d_annuler_un_evenement_deja_annule() {
        Event event = new Event("Concert", null, Instant.now(), null, venue(), UUID.randomUUID());
        event.annuler();

        assertThatThrownBy(event::annuler)
            .isInstanceOf(BusinessConflictException.class)
            .hasMessage("L'événement est déjà annulé");
        assertThat(event.getStatus()).isEqualTo(EventStatus.ANNULE);
    }

    // Pas de test sur "refuse d'annuler un événement TERMINE" : aucun mécanisme
    // ne permet encore d'atteindre ce statut (pas de setter, pas de transition
    // automatique). À ajouter dès qu'un tel mécanisme existe (ex. job planifié
    // en V2) — un test sur un état inatteignable passerait sans rien vérifier.
}
