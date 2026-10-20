package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import com.billetplus.auth.infrastructure.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder, tokenProvider);
    }

    // ---------------------------------------------------------------- utilisateur courant

    @Test
    void retourne_l_utilisateur_correspondant_a_l_id() {
        User user = new User("test@example.com", "hash", "Test", Role.AGENT);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = service().getCurrentUser(user.getId());

        assertThat(result).isEqualTo(user);
    }

    @Test
    void leve_une_exception_si_l_utilisateur_n_existe_plus() {
        UUID idInconnu = UUID.randomUUID();
        when(userRepository.findById(idInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getCurrentUser(idInconnu))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ---------------------------------------------------------------- inscription publique

    @Test
    void l_inscription_publique_cree_un_compte_organisateur_avec_le_mot_de_passe_hache() {
        when(userRepository.existsByEmail("orga@example.com")).thenReturn(false);
        when(passwordEncoder.encode("MotDePasse123")).thenReturn("hash-bcrypt");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service().registerOrganisateur("orga@example.com", "MotDePasse123", "Orga Un");

        // Règle de sécurité centrale : le rôle n'est jamais choisi par l'appelant.
        assertThat(result.getRole()).isEqualTo(Role.ORGANISATEUR);
        // Le mot de passe en clair n'est jamais stocké : seul le hash l'est.
        assertThat(result.getMotDePasseHash()).isEqualTo("hash-bcrypt");
        assertThat(result.getEmail()).isEqualTo("orga@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void refuse_une_inscription_avec_un_email_deja_utilise_sans_rien_sauvegarder() {
        when(userRepository.existsByEmail("deja@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service().registerOrganisateur("deja@example.com", "MotDePasse123", "Doublon"))
            .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }
}
