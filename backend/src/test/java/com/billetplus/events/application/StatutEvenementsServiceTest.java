package com.billetplus.events.application;

import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.Venue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatutEvenementsServiceTest {

    @Mock
    private EventRepository eventRepository;

    private StatutEvenementsService service() {
        return new StatutEvenementsService(eventRepository);
    }

    private Event evenement() {
        return new Event("Concert", null, Instant.now(), null, new Venue("Grand Theatre", "Dakar", null, null), UUID.randomUUID());
    }

    @Test
    void seul_un_evenement_publie_est_ouvert_a_la_vente() {
        Event brouillon = evenement();
        Event publie = evenement();
        publie.publier();
        Event annule = evenement();
        annule.publier();
        annule.annuler();
        for (Event e : new Event[]{brouillon, publie, annule}) {
            when(eventRepository.findById(e.getId())).thenReturn(Optional.of(e));
        }

        assertThat(service().estOuvertALaVente(brouillon.getId())).isFalse();
        assertThat(service().estOuvertALaVente(publie.getId())).isTrue();
        assertThat(service().estOuvertALaVente(annule.getId())).isFalse();
    }

    @Test
    void un_evenement_inconnu_n_est_pas_ouvert_a_la_vente() {
        UUID inconnu = UUID.randomUUID();
        when(eventRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThat(service().estOuvertALaVente(inconnu)).isFalse();
    }

    @Test
    void un_identifiant_absent_n_est_pas_ouvert_a_la_vente_sans_lire_la_base() {
        assertThat(service().estOuvertALaVente(null)).isFalse();
        verifyNoInteractions(eventRepository);
    }
}
