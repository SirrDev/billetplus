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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;

    @Test
    void retourne_l_utilisateur_correspondant_a_l_id() {
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenProvider);
        User user = new User("test@example.com", "hash", "Test", Role.AGENT);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = service.getCurrentUser(user.getId());

        assertThat(result).isEqualTo(user);
    }

    @Test
    void leve_une_exception_si_l_utilisateur_n_existe_plus() {
        AuthService service = new AuthService(userRepository, passwordEncoder, tokenProvider);
        UUID idInconnu = UUID.randomUUID();
        when(userRepository.findById(idInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(idInconnu))
            .isInstanceOf(NoSuchElementException.class);
    }
}
