package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComptesServiceTest {

    @Mock
    private UserRepository userRepository;

    private ComptesService service() {
        return new ComptesService(userRepository, mock(VerificationRepository.class));
    }

    @Test
    void reconnait_un_agent_de_son_organisateur_et_de_lui_seul() {
        UUID organisateur = UUID.randomUUID();
        User agent = User.agentCreePar(organisateur, "a@billetplus.sn", "hash", "A");
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThat(service().estAgentCreePar(agent.getId(), organisateur)).isTrue();
        assertThat(service().estAgentCreePar(agent.getId(), UUID.randomUUID())).isFalse();
    }

    /** Un organisateur n'est pas un agent, même s'il est passé à la place d'un agent. */
    @Test
    void un_compte_qui_n_est_pas_agent_n_est_jamais_reconnu() {
        User autreOrganisateur = new User("o@billetplus.sn", "hash", "O", Role.ORGANISATEUR);
        when(userRepository.findById(autreOrganisateur.getId())).thenReturn(Optional.of(autreOrganisateur));
        UUID inconnu = UUID.randomUUID();
        when(userRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThat(service().estAgentCreePar(autreOrganisateur.getId(), UUID.randomUUID())).isFalse();
        assertThat(service().estAgentCreePar(inconnu, UUID.randomUUID())).isFalse();
    }

    @Test
    void seul_un_agent_non_retire_est_actif() {
        UUID organisateur = UUID.randomUUID();
        User actif = User.agentCreePar(organisateur, "actif@billetplus.sn", "hash", "Actif");
        User retire = User.agentCreePar(organisateur, "retire@billetplus.sn", "hash", "Retire");
        retire.desactiver();
        User orga = new User("orga@billetplus.sn", "hash", "Orga", Role.ORGANISATEUR);
        UUID inconnu = UUID.randomUUID();
        when(userRepository.findById(actif.getId())).thenReturn(Optional.of(actif));
        when(userRepository.findById(retire.getId())).thenReturn(Optional.of(retire));
        when(userRepository.findById(orga.getId())).thenReturn(Optional.of(orga));
        when(userRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThat(service().estAgentActif(actif.getId())).isTrue();
        assertThat(service().estAgentActif(retire.getId())).isFalse();
        assertThat(service().estAgentActif(orga.getId())).isFalse();
        assertThat(service().estAgentActif(inconnu)).isFalse();
        assertThat(service().estAgentActif(null)).isFalse();
    }

    @Test
    void un_identifiant_absent_n_est_jamais_reconnu_sans_lire_la_base() {
        assertThat(service().estAgentCreePar(null, UUID.randomUUID())).isFalse();
        assertThat(service().estAgentCreePar(UUID.randomUUID(), null)).isFalse();
        verifyNoInteractions(userRepository);
    }
}
