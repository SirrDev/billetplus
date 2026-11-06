package com.billetplus.events.application;

import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.Venue;
import com.billetplus.shared.exception.BusinessConflictException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** EventService#modifier : contrôle de propriété, identique à publier / annuler. */
@ExtendWith(MockitoExtension.class)
class EventServiceModificationTest {

    private static final Instant DEBUT = Instant.parse("2026-12-05T20:00:00Z");

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private ApplicationEventPublisher publisher;

    private EventService service() {
        return new EventService(eventRepository, venueRepository, publisher);
    }

    private Event brouillonDe(UUID proprietaire) {
        return new Event("Concert", null, DEBUT, null, new Venue("Sorano", "Dakar", null, null, com.billetplus.events.domain.VillesDeTest.dakar()), proprietaire);
    }

    @Test
    void la_position_passe_par_une_copie_du_lieu_sans_toucher_l_original() {
        UUID orga = UUID.randomUUID();
        Event event = brouillonDe(orga);
        Venue original = event.getVenue();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(venueRepository.save(any(Venue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.save(event)).thenReturn(event);

        Event modifie = service().definirPosition(event.getId(), 14.7, -17.4, orga);

        assertThat(modifie.getVenue()).isNotSameAs(original);
        assertThat(modifie.getVenue().getNom()).isEqualTo("Sorano");
        assertThat(modifie.getVenue().getLatitude()).isEqualTo(14.7);
        assertThat(modifie.getVenue().getLongitude()).isEqualTo(-17.4);
        assertThat(original.getLatitude()).isNull();
    }

    @Test
    void un_autre_organisateur_ne_change_pas_la_position() {
        Event event = brouillonDe(UUID.randomUUID());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().definirPosition(event.getId(), 14.7, -17.4, UUID.randomUUID()))
            .isInstanceOf(NoSuchElementException.class);

        verify(venueRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void un_evenement_annule_ne_change_plus_de_position() {
        UUID orga = UUID.randomUUID();
        Event event = brouillonDe(orga);
        event.annuler();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().definirPosition(event.getId(), 14.7, -17.4, orga))
            .isInstanceOf(BusinessConflictException.class);

        // Refus constaté AVANT la copie : aucune ligne venues écrite, même annulée ensuite.
        verify(venueRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    /** Décision du 06/11/2026 : une fois publié, le repère est figé, comme les dates. */
    @Test
    void la_position_d_un_evenement_publie_ne_change_plus() {
        UUID orga = UUID.randomUUID();
        Event event = brouillonDe(orga);
        event.publier();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().definirPosition(event.getId(), 14.7, -17.4, orga))
            .isInstanceOf(BusinessConflictException.class)
            .hasMessage("La position d'un événement publié ne peut plus changer");

        verify(venueRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void une_position_identique_ne_cree_pas_de_lieu() {
        UUID orga = UUID.randomUUID();
        Event event = new Event("Concert", null, DEBUT, null, new Venue("Sorano", "Dakar", 14.7, -17.4, com.billetplus.events.domain.VillesDeTest.dakar()), orga);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        Event resultat = service().definirPosition(event.getId(), 14.7, -17.4, orga);

        assertThat(resultat.getVenue()).isSameAs(event.getVenue());
        verify(venueRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void l_organisateur_modifie_son_evenement_et_il_est_enregistre() {
        UUID orga = UUID.randomUUID();
        Event event = brouillonDe(orga);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        Event resultat = service().modifier(event.getId(), "Nouveau", "Desc", DEBUT, null, null, orga);

        assertThat(resultat.getTitre()).isEqualTo("Nouveau");
        verify(eventRepository).save(event);
    }

    @Test
    void le_brouillon_d_un_autre_repond_introuvable() {
        Event event = brouillonDe(UUID.randomUUID());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().modifier(event.getId(), "Pirate", null, DEBUT, null, null, UUID.randomUUID()))
            .isInstanceOf(NoSuchElementException.class);
        assertThat(event.getTitre()).isEqualTo("Concert");
        verify(eventRepository, never()).save(any());
    }

    @Test
    void l_evenement_publie_d_un_autre_est_refuse() {
        Event event = brouillonDe(UUID.randomUUID());
        event.publier();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().modifier(event.getId(), "Pirate", null, DEBUT, null, null, UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class);
        assertThat(event.getTitre()).isEqualTo("Concert");
        verify(eventRepository, never()).save(any());
    }
}
