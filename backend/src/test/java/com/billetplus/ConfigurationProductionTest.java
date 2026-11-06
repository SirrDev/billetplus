package com.billetplus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Réglages du profil prod lus directement dans application.yml, sans contexte Spring :
 * ConfidentialiteErreursSqlIntegrationTest vérifie l'effet du réglage sur le pilote, ce test
 * vérifie qu'il reste bien dans le bloc « on-profile: prod » (déplacé ou supprimé : rouge).
 */
class ConfigurationProductionTest {

    private static PropertySource<?> blocProd() throws IOException {
        List<PropertySource<?>> documents =
            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        return documents.stream()
            .filter(document -> "prod".equals(String.valueOf(document.getProperty("spring.config.activate.on-profile"))))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Aucun bloc on-profile: prod dans application.yml"));
    }

    @Test
    void en_production_les_erreurs_sql_ne_recopient_pas_les_valeurs_saisies() throws IOException {
        assertThat(String.valueOf(blocProd().getProperty("spring.datasource.hikari.data-source-properties.logServerErrorDetail")))
            .isEqualTo("false");
    }
}
