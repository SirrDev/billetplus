package com.billetplus;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /actuator/health : publique (sonde du conteneur et du proxy), sans détail. Aucun
 * autre endpoint actuator n'est exposé (application.yml, management.endpoints).
 */
class SondeSanteHttpIntegrationTest extends ContexteCompletTestBase {

    @Test
    void la_sonde_de_sante_est_publique_et_ne_donne_aucun_detail() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void les_autres_endpoints_actuator_ne_sont_pas_exposes() throws Exception {
        // Anonyme : 401 (règle anyRequest().authenticated()) ; connecté : 404 (non exposés).
        for (String endpoint : new String[] {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator"}) {
            mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
            mvc.perform(get(endpoint).with(connecte(java.util.UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isNotFound());
        }
    }
}
