package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import com.billetplus.shared.evenements.AgentRetire;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.NoSuchElementException;
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
    @Mock
    private ApplicationEventPublisher publisher;

    private final User organisateur = new User("orga@billetplus.sn", "hash", "Orga", Role.ORGANISATEUR);

    private AgentAccountService service() {
        return new AgentAccountService(userRepository, passwordEncoder, publisher);
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

    private User agentActif() {
        when(userRepository.findById(organisateur.getId())).thenReturn(Optional.of(organisateur));
        User agent = User.agentCreePar(organisateur.getId(), "agent@billetplus.sn", "hash", "Agent Un");
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        return agent;
    }

    @Test
    void modifie_le_nom_et_l_email_de_son_agent() {
        User agent = agentActif();
        when(userRepository.existsByEmail("Nouveau@billetplus.sn")).thenReturn(false);
        when(userRepository.save(agent)).thenReturn(agent);

        User modifie = service().modifierAgent(organisateur.getId(), agent.getId(), "Nouveau@billetplus.sn", "Agent Deux");

        assertThat(modifie.getEmail()).isEqualTo("nouveau@billetplus.sn");
        assertThat(modifie.getNomComplet()).isEqualTo("Agent Deux");
    }

    /** Garder son propre e-mail, même écrit autrement, ne doit pas être pris pour un doublon. */
    @Test
    void garder_le_meme_email_n_est_pas_un_doublon() {
        User agent = agentActif();
        when(userRepository.save(agent)).thenReturn(agent);

        service().modifierAgent(organisateur.getId(), agent.getId(), " AGENT@billetplus.sn ", "Agent Deux");

        verify(userRepository, never()).existsByEmail(any());
        assertThat(agent.getNomComplet()).isEqualTo("Agent Deux");
    }

    @Test
    void refuse_un_email_pris_par_un_autre_compte() {
        User agent = agentActif();
        when(userRepository.existsByEmail("pris@billetplus.sn")).thenReturn(true);

        assertThatThrownBy(() -> service().modifierAgent(organisateur.getId(), agent.getId(), "pris@billetplus.sn", "X"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un compte existe déjà avec cet email");

        verify(userRepository, never()).save(any());
    }

    @Test
    void un_organisateur_ne_touche_pas_a_l_agent_d_un_autre() {
        when(userRepository.findById(organisateur.getId())).thenReturn(Optional.of(organisateur));
        User autre = User.agentCreePar(UUID.randomUUID(), "autre@billetplus.sn", "hash", "Autre");
        when(userRepository.findById(autre.getId())).thenReturn(Optional.of(autre));

        assertThatThrownBy(() -> service().modifierAgent(organisateur.getId(), autre.getId(), "x@billetplus.sn", "X"))
            .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service().retirerAgent(organisateur.getId(), autre.getId()))
            .isInstanceOf(NoSuchElementException.class);

        assertThat(autre.estActif()).isTrue();
        verifyNoInteractions(publisher);
    }

    @Test
    void retirer_desactive_le_compte_et_previent_les_autres_modules() {
        User agent = agentActif();

        service().retirerAgent(organisateur.getId(), agent.getId());

        assertThat(agent.estActif()).isFalse();
        verify(userRepository).save(agent);
        verify(publisher).publishEvent(new AgentRetire(agent.getId()));
    }

    @Test
    void retirer_deux_fois_ne_change_rien() {
        User agent = agentActif();
        agent.desactiver();

        service().retirerAgent(organisateur.getId(), agent.getId());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void un_agent_retire_ne_se_modifie_plus_et_disparait_de_la_liste() {
        User agent = agentActif();
        agent.desactiver();
        when(userRepository.findByCreePar(organisateur.getId())).thenReturn(List.of(agent));

        assertThatThrownBy(() -> service().modifierAgent(organisateur.getId(), agent.getId(), "x@billetplus.sn", "X"))
            .isInstanceOf(NoSuchElementException.class);
        assertThat(service().listerMesAgents(organisateur.getId())).isEmpty();
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
