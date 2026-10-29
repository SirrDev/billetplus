package com.billetplus.events.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EchappementJokersTest {

    @Test
    void les_jokers_de_like_et_la_barre_oblique_inverse_sont_echappes() {
        assertThat(JpaEventRepositoryAdapter.echapperJokers("100%")).isEqualTo("100\\%");
        assertThat(JpaEventRepositoryAdapter.echapperJokers("a_b")).isEqualTo("a\\_b");
        // La barre elle-même d'abord : sinon « \% » deviendrait un % non échappé.
        assertThat(JpaEventRepositoryAdapter.echapperJokers("a\\%")).isEqualTo("a\\\\\\%");
    }

    @Test
    void un_texte_ordinaire_est_inchange() {
        assertThat(JpaEventRepositoryAdapter.echapperJokers("Concert Youssou")).isEqualTo("Concert Youssou");
        assertThat(JpaEventRepositoryAdapter.echapperJokers("")).isEmpty();
    }
}
