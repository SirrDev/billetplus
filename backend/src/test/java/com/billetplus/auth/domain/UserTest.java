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
}
