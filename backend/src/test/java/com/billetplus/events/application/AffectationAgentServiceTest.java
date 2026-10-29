package com.billetplus.events.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.domain.AffectationAgent;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.Venue;
import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AffectationAgentServiceTest {

    @Mock
    private EventService eventService;
    @Mock
    private AffectationAgentRepository affectationRepository;
    @Mock
    private Comptes comptes;

    private final UUID organisateur = UUID.randomUUID();
    private final UUID agent = UUID.randomUUID();

    private AffectationAgentService service() {
        return new AffectationAgentService(eventService, affectationRepository, comptes);
    }

    private Event evenement() {
        Event event = new Event("Concert", null, Instant.now(), null, new Venue("Grand Theatre", "Dakar", null, null), organisateur);
        when(eventService.get(event.getId())).thenReturn(event);
        return event;
    }

    @Test
    void l_organisateur_affecte_un_de_ses_agents() {
        Event event = evenement();
        when(comptes.estAgentCreePar(agent, organisateur)).thenReturn(true);
        when(affectationRepository.existe(event.getId(), agent)).thenReturn(false);

        service().affecter(event.getId(), agent, organisateur);

        verify(affectationRepository).save(any(AffectationAgent.class));
    }

    @Test
    void reaffecter_un_agent_deja_affecte_ne_change_rien() {
        Event event = evenement();
        when(comptes.estAgentCreePar(agent, organisateur)).thenReturn(true);
        when(affectationRepository.existe(event.getId(), agent)).thenReturn(true);

        service().affecter(event.getId(), agent, organisateur);

        verify(affectationRepository, never()).save(any());
    }

    /** L'agent d'un autre organisateur, ou un identifiant inconnu : même 404. */
    @Test
    void refuse_l_agent_d_un_autre_organisateur_comme_un_agent_inconnu() {
        Event event = evenement();
        when(comptes.estAgentCreePar(agent, organisateur)).thenReturn(false);

        assertThatThrownBy(() -> service().affecter(event.getId(), agent, organisateur))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Agent introuvable : " + agent);

        verify(affectationRepository, never()).save(any());
    }

    /** Même règle qu'EventService.publier / annuler : un non-propriétaire reçoit 403. */
    @Test
    void un_autre_organisateur_ne_peut_ni_affecter_ni_retirer() {
        Event event = evenement();
        UUID autre = UUID.randomUUID();

        assertThatThrownBy(() -> service().affecter(event.getId(), agent, autre)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service().retirer(event.getId(), agent, autre)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(comptes, affectationRepository);
    }

    @Test
    void refuse_d_affecter_un_agent_a_un_evenement_annule() {
        Event event = evenement();
        event.annuler();

        assertThatThrownBy(() -> service().affecter(event.getId(), agent, organisateur))
            .isInstanceOf(BusinessConflictException.class);

        verifyNoInteractions(comptes, affectationRepository);
    }

    @Test
    void l_organisateur_retire_un_agent() {
        Event event = evenement();

        service().retirer(event.getId(), agent, organisateur);

        verify(affectationRepository).supprimer(event.getId(), agent);
    }
}
