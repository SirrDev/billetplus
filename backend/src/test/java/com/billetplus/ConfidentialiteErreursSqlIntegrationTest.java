package com.billetplus;

import com.billetplus.auth.application.AuthService;
import com.billetplus.auth.application.UserRepository;
import com.billetplus.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Effet du réglage du profil prod logServerErrorDetail=false sur le pilote PostgreSQL (sa présence
 * dans application.yml est vérifiée par ConfigurationProductionTest). Contexte à part
 * (propriété différente, environ 25 s de démarrage en plus), accepté pour ce seul test :
 * il vérifie que l'e-mail ne figure plus dans le message d'erreur SQL, et que le doublon
 * est toujours reconnu par le nom de sa contrainte.
 */
@TestPropertySource(properties = "spring.datasource.hikari.data-source-properties.logServerErrorDetail=false")
class ConfidentialiteErreursSqlIntegrationTest extends ContexteCompletTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void le_message_d_erreur_sql_ne_contient_plus_l_email_mais_le_doublon_reste_reconnu() {
        String email = "secret-" + UUID.randomUUID() + "@billetplus.sn";
        UUID orga = authService.registerOrganisateur(email, "motdepasse", "Premier").getId();
        User doublon = User.agentCreePar(orga, email, "hash", "Second");

        Throwable erreur = catchThrowable(() -> new TransactionTemplate(transactionManager)
            .executeWithoutResult(statut -> userRepository.save(doublon)));

        assertThat(erreur).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un compte existe déjà avec cet email");
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            assertThat(String.valueOf(cause.getMessage())).doesNotContain(email);
        }
    }
}
