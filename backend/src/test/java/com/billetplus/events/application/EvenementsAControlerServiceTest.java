package com.billetplus.events.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.domain.Event;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EvenementsAControlerServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-14T21:00:00Z");

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final Comptes comptes = mock(Comptes.class);
    private final EvenementsAControlerService service =
        new EvenementsAControlerService(eventRepository, comptes, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    @Test
    void cherche_les_evenements_de_l_agent_avec_la_fenetre_de_l_horloge() {
        UUID agent = UUID.randomUUID();
        when(comptes.estAgentActif(agent)).thenReturn(true);
        List<Event> attendus = List.of();
        // Début dans les 24 dernières heures ou à venir ; ou fin encore à venir.
        when(eventRepository.findAffectesA(agent, Instant.parse("2026-11-13T21:00:00Z"), MAINTENANT)).thenReturn(attendus);

        assertThat(service.pourLAgent(agent)).isSameAs(attendus);
    }

    @Test
    void un_agent_retire_n_a_plus_aucun_evenement_a_controler() {
        UUID retire = UUID.randomUUID();
        when(comptes.estAgentActif(retire)).thenReturn(false);

        assertThat(service.pourLAgent(retire)).isEmpty();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void refuse_un_agent_absent() {
        assertThatThrownBy(() -> service.pourLAgent(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventRepository);
    }
}
