package com.billetplus.events.application;

import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.EventStatus;
import com.billetplus.events.domain.Venue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    private EventService service() {
        return new EventService(eventRepository, venueRepository, publisher);
    }

    private Venue venue() {
        return new Venue("Grand Théâtre", "Dakar", null, null);
    }

    @Test
    void cree_un_evenement_quand_le_lieu_existe() {
        Venue venue = venue();
        when(venueRepository.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event event = service().create("Concert", "Desc", Instant.now(), null, venue.getId(), UUID.randomUUID());

        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void refuse_de_creer_un_evenement_sur_un_lieu_inexistant() {
        UUID venueIdInconnu = UUID.randomUUID();
        when(venueRepository.findById(venueIdInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service().create("Concert", null, Instant.now(), null, venueIdInconnu, UUID.randomUUID())
        ).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void permet_a_l_organisateur_de_publier_son_propre_evenement() {
        UUID organizerId = UUID.randomUUID();
        Event event = new Event("Concert", null, Instant.now(), null, venue(), organizerId);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event result = service().publier(event.getId(), organizerId);

        assertThat(result.getStatus()).isEqualTo(EventStatus.PUBLIE);
    }

    @Test
    void refuse_qu_un_autre_utilisateur_publie_l_evenement() {
        UUID organizerId = UUID.randomUUID();
        UUID autreUtilisateur = UUID.randomUUID();
        Event event = new Event("Concert", null, Instant.now(), null, venue(), organizerId);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        // Brouillon d'un autre : 404, indiscernable d'un identifiant inconnu.
        assertThatThrownBy(() -> service().publier(event.getId(), autreUtilisateur))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Événement introuvable : " + event.getId());

        verify(eventRepository, never()).save(any());
    }

    @Test
    void refuse_qu_un_autre_utilisateur_annule_l_evenement() {
        UUID organizerId = UUID.randomUUID();
        UUID autreUtilisateur = UUID.randomUUID();
        Event event = new Event("Concert", null, Instant.now(), null, venue(), organizerId);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().annuler(event.getId(), autreUtilisateur))
            .isInstanceOf(NoSuchElementException.class);
    }

    /** Événement publié : son existence est publique, un autre organisateur reçoit 403. */
    @Test
    void refuse_en_403_qu_un_autre_utilisateur_annule_un_evenement_publie() {
        UUID organizerId = UUID.randomUUID();
        Event event = new Event("Concert", null, Instant.now(), null, venue(), organizerId);
        event.publier();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().annuler(event.getId(), UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class);
        verify(eventRepository, never()).save(any());
    }
}
