package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.UserRepository;
import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PremierAdminInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PremierAdminInitializer initializer(String email, String motDePasse) {
        return new PremierAdminInitializer(userRepository, passwordEncoder, email, motDePasse);
    }

    /** Cas normal en dev et en CI : aucune variable, rien n'est fait. */
    @Test
    void sans_variables_ne_fait_rien() {
        initializer("", "").run(null);
        initializer(null, null).run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void cree_le_premier_admin_avec_un_mot_de_passe_hache() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@billetplus.sn")).thenReturn(false);
        when(passwordEncoder.encode("un-mot-de-passe-solide")).thenReturn("hash-bcrypt");

        initializer("admin@billetplus.sn", "un-mot-de-passe-solide").run(null);

        ArgumentCaptor<User> cree = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cree.capture());
        assertThat(cree.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(cree.getValue().getEmail()).isEqualTo("admin@billetplus.sn");
        assertThat(cree.getValue().getMotDePasseHash()).isEqualTo("hash-bcrypt");
    }

    /** Un ADMIN existe déjà : rien n'est créé, les variables peuvent être retirées. */
    @Test
    void ne_cree_rien_si_un_admin_existe_deja() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        initializer("admin@billetplus.sn", "un-mot-de-passe-solide").run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    /** Configuration invalide : démarrage refusé plutôt qu'un compte d'administration faible ou incomplet. */
    @Test
    void refuse_de_demarrer_avec_une_configuration_invalide() {
        assertThatThrownBy(() -> initializer("admin@billetplus.sn", "").run(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("", "un-mot-de-passe-solide").run(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("admin@billetplus.sn", "court").run(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("admin@billetplus.sn", "x".repeat(73)).run(null))
            .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void refuse_de_demarrer_si_l_email_appartient_a_un_compte_qui_n_est_pas_admin() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("orga@billetplus.sn")).thenReturn(true);

        assertThatThrownBy(() -> initializer("orga@billetplus.sn", "un-mot-de-passe-solide").run(null))
            .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }
}
