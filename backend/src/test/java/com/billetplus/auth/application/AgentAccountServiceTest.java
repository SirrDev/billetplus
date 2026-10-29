package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentAccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final User organisateur = new User("orga@billetplus.sn", "hash", "Orga", Role.ORGANISATEUR);

    private AgentAccountService service() {
        return new AgentAccountService(userRepository, passwordEncoder);
    }

    @Test
    void un_organisateur_cree_un_agent_qui_lui_appartient_avec_un_mot_de_passe_hache() {
        when(userRepository.findById(organisateur.getId())).thenReturn(Optional.of(organisateur));
        when(userRepository.existsByEmail("agent@billetplus.sn")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse")).thenReturn("hash-bcrypt");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User agent = service().creerAgent(organisateur.getId(), "agent@billetplus.sn", "motdepasse", "Agent Un");

        assertThat(agent.getRole()).isEqualTo(Role.AGENT);
        assertThat(agent.getCreePar()).isEqualTo(organisateur.getId());
        assertThat(agent.getMotDePasseHash()).isEqualTo("hash-bcrypt");
    }

    /** Même message qu'à l'inscription (énumération d'emails : risque connu, section 7 du HANDOFF). */
    @Test
    void refuse_un_email_deja_utilise() {
        when(userRepository.findById(organisateur.getId())).thenReturn(Optional.of(organisateur));
        when(userRepository.existsByEmail("pris@billetplus.sn")).thenReturn(true);

        assertThatThrownBy(() -> service().creerAgent(organisateur.getId(), "pris@billetplus.sn", "motdepasse", "X"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un compte existe déjà avec cet email");

        verify(userRepository, never()).save(any());
    }

    /** Vérifié en base, en plus du @PreAuthorize de la route : un AGENT ou un inconnu ne crée pas d'agent. */
    @Test
    void seul_un_organisateur_peut_creer_ou_lister_des_agents() {
        User agent = User.agentCreePar(organisateur.getId(), "a@billetplus.sn", "hash", "A");
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        UUID inconnu = UUID.randomUUID();
        when(userRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().creerAgent(agent.getId(), "b@billetplus.sn", "motdepasse", "B"))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service().listerMesAgents(inconnu))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service().creerAgent(null, "c@billetplus.sn", "motdepasse", "C"))
            .isInstanceOf(AccessDeniedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void liste_les_agents_crees_par_l_organisateur() {
        User agent = User.agentCreePar(organisateur.getId(), "a@billetplus.sn", "hash", "A");
        when(userRepository.findById(organisateur.getId())).thenReturn(Optional.of(organisateur));
        when(userRepository.findByCreePar(organisateur.getId())).thenReturn(List.of(agent));

        assertThat(service().listerMesAgents(organisateur.getId())).containsExactly(agent);
    }
}
