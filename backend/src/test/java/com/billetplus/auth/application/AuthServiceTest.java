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

    // ---------------------------------------------------------------- agent retiré

    private User agentRetire() {
        User agent = User.agentCreePar(UUID.randomUUID(), "agent@example.com", "hash", "Agent");
        agent.desactiver();
        return agent;
    }

    @Test
    void un_agent_retire_ne_se_connecte_plus_meme_avec_le_bon_mot_de_passe() {
        User agent = agentRetire();
        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(agent));
        when(passwordEncoder.matches("motdepasse", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service().login("agent@example.com", "motdepasse"))
            .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class)
            .hasMessage("Identifiants invalides");
    }

    /** Son jeton d'accès vit encore : ni /me ni changement de mot de passe (getCurrentUser). */
    @Test
    void un_agent_retire_n_accede_plus_a_son_compte_avec_un_jeton_encore_valide() {
        User agent = agentRetire();
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service().getCurrentUser(agent.getId()))
            .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThatThrownBy(() -> service().changerMotDePasse(agent.getId(), "ancien", "nouveaumotdepasse"))
            .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void un_agent_retire_ne_rafraichit_plus_ses_jetons() {
        User agent = agentRetire();
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        when(tokenProvider.parseAndValidate("refresh")).thenReturn(claims);
        when(tokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(tokenProvider.getUserId(claims)).thenReturn(agent.getId());
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service().refresh("refresh"))
            .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
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
