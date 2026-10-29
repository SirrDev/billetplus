package com.billetplus.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void normalise_email_en_minuscules() {
        User user = new User("Test@Example.COM", "hash", "Test User", Role.ORGANISATEUR);

        assertThat(user.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void rejette_un_email_vide() {
        assertThatThrownBy(() -> new User("", "hash", "Test", Role.AGENT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejette_un_mot_de_passe_vide() {
        assertThatThrownBy(() -> new User("test@example.com", "", "Test", Role.AGENT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identifie_correctement_le_role_organisateur() {
        User user = new User("orga@example.com", "hash", "Orga", Role.ORGANISATEUR);

        assertThat(user.estOrganisateur()).isTrue();
        assertThat(user.estAgent()).isFalse();
        assertThat(user.estAdmin()).isFalse();
    }

    @Test
    void un_agent_est_toujours_cree_par_un_organisateur_qui_en_est_proprietaire() {
        java.util.UUID organisateur = java.util.UUID.randomUUID();

        User agent = User.agentCreePar(organisateur, "Agent@Example.COM", "hash", "Agent");

        assertThat(agent.estAgent()).isTrue();
        assertThat(agent.getCreePar()).isEqualTo(organisateur);
        assertThat(agent.getEmail()).isEqualTo("agent@example.com");
        assertThatThrownBy(() -> User.agentCreePar(null, "a@example.com", "hash", "A"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_compte_inscrit_n_a_pas_de_createur() {
        assertThat(new User("orga@example.com", "hash", "Orga", Role.ORGANISATEUR).getCreePar()).isNull();
    }
}
