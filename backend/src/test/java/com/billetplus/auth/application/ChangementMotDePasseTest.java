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

/** AuthService#changerMotDePasse. */
@ExtendWith(MockitoExtension.class)
class ChangementMotDePasseTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder, tokenProvider);
    }

    private User compte() {
        User user = new User("orga@billetplus.sn", "ancien-hash", "Orga", Role.ORGANISATEUR);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void remplace_le_hash_quand_l_actuel_est_correct() {
        User user = compte();
        when(passwordEncoder.matches("ancienMotDePasse", "ancien-hash")).thenReturn(true);
        when(passwordEncoder.encode("nouveauMotDePasse")).thenReturn("nouveau-hash");

        service().changerMotDePasse(user.getId(), "ancienMotDePasse", "nouveauMotDePasse");

        assertThat(user.getMotDePasseHash()).isEqualTo("nouveau-hash");
        verify(userRepository).save(user);
    }

    @Test
    void refuse_un_mot_de_passe_actuel_incorrect_sans_rien_changer() {
        User user = compte();
        when(passwordEncoder.matches("faux", "ancien-hash")).thenReturn(false);

        assertThatThrownBy(() -> service().changerMotDePasse(user.getId(), "faux", "nouveauMotDePasse"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Le mot de passe actuel est incorrect");
        assertThat(user.getMotDePasseHash()).isEqualTo("ancien-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void refuse_un_nouveau_mot_de_passe_identique_a_l_actuel() {
        User user = compte();
        when(passwordEncoder.matches("memeMotDePasse", "ancien-hash")).thenReturn(true);

        assertThatThrownBy(() -> service().changerMotDePasse(user.getId(), "memeMotDePasse", "memeMotDePasse"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void un_compte_disparu_repond_introuvable() {
        UUID inconnu = UUID.randomUUID();
        when(userRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().changerMotDePasse(inconnu, "a", "b"))
            .isInstanceOf(NoSuchElementException.class);
    }
}
