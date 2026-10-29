package com.billetplus.events.application;

import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.EventStatus;
import com.billetplus.events.domain.Venue;
import com.billetplus.shared.evenements.EvenementAnnule;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles d'accès du module events : visibilité des brouillons, contrôle de
 * propriété et conflit de statut. Tests unitaires purs (aucun contexte Spring,
 * aucune base) : c'est le bénéfice recherché du niveau 2 (port + JPA-as-domain,
 * voir ADR 0002).
 *
 * Complète EventServiceTest (création et cas nominaux) sans le dupliquer.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceAccessTest {

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
        Venue venue = new Venue("Grand Theatre", "Dakar", null, null);
        return new Event("Concert", null, Instant.now(), null, venue, proprietaire);
    }

    // ---------------------------------------------------------------- visibilité

    @Test
    void un_brouillon_est_invisible_pour_un_visiteur_anonyme() {
        Event event = brouillonDe(UUID.randomUUID());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().getVisible(event.getId(), null))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void un_brouillon_est_invisible_pour_un_autre_utilisateur() {
        Event event = brouillonDe(UUID.randomUUID());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().getVisible(event.getId(), UUID.randomUUID()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void un_brouillon_est_visible_pour_son_proprietaire() {
        UUID proprietaire = UUID.randomUUID();
        Event event = brouillonDe(proprietaire);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        Event result = service().getVisible(event.getId(), proprietaire);

        assertThat(result).isSameAs(event);
    }

    @Test
    void un_evenement_publie_est_visible_pour_un_visiteur_anonyme() {
        Event event = brouillonDe(UUID.randomUUID());
        event.publier();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        Event result = service().getVisible(event.getId(), null);

        assertThat(result).isSameAs(event);
    }

    @Test
    void un_evenement_annule_reste_visible_publiquement() {
        // Les acheteurs doivent pouvoir constater l'annulation.
        Event event = brouillonDe(UUID.randomUUID());
        event.annuler();
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        Event result = service().getVisible(event.getId(), null);

        assertThat(result.getStatus()).isEqualTo(EventStatus.ANNULE);
    }

    @Test
    void un_identifiant_inconnu_donne_la_meme_erreur_qu_un_brouillon_cache() {
        UUID idInconnu = UUID.randomUUID();
        when(eventRepository.findById(idInconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getVisible(idInconnu, null))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ---------------------------------------------------------------- propriété

    @Test
    void publier_par_un_non_proprietaire_est_refuse_sans_rien_sauvegarder() {
        Event event = brouillonDe(UUID.randomUUID());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().publier(event.getId(), UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never()).save(any());
        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
    }

    @Test
    void annuler_par_un_non_proprietaire_est_refuse_sans_rien_sauvegarder() {
        Event event = brouillonDe(UUID.randomUUID());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().annuler(event.getId(), UUID.randomUUID()))
            .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never()).save(any());
        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
        // Annulation refusée : les modules qui écoutent (tickets) ne sont pas prévenus.
        verifyNoInteractions(publisher);
    }

    // ---------------------------------------------------------------- annulation (ADR 0005)

    @Test
    void annuler_publie_l_evenement_annule_pour_les_autres_modules() {
        UUID proprietaire = UUID.randomUUID();
        Event event = brouillonDe(proprietaire);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        service().annuler(event.getId(), proprietaire);

        assertThat(event.getStatus()).isEqualTo(EventStatus.ANNULE);
        verify(publisher).publishEvent(new EvenementAnnule(event.getId()));
    }

    /** Si l'annulation est refusée par le domaine (événement terminé), rien n'est publié. */
    @Test
    void une_annulation_refusee_par_le_domaine_ne_publie_rien() {
        UUID proprietaire = UUID.randomUUID();
        Event event = mock(Event.class);
        when(event.getOrganizerId()).thenReturn(proprietaire);
        doThrow(new BusinessConflictException("Impossible d'annuler un événement déjà terminé")).when(event).annuler();
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service().annuler(eventId, proprietaire))
            .isInstanceOf(BusinessConflictException.class);

        verify(eventRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    // ---------------------------------------------------------------- conflit de statut

    @Test
    void publier_deux_fois_leve_un_conflit_metier() {
        UUID proprietaire = UUID.randomUUID();
        Event event = brouillonDe(proprietaire);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        service().publier(event.getId(), proprietaire);

        assertThatThrownBy(() -> service().publier(event.getId(), proprietaire))
            .isInstanceOf(BusinessConflictException.class);

        // Une seule sauvegarde : la seconde tentative échoue avant d'écrire.
        verify(eventRepository, times(1)).save(any(Event.class));
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLIE);
    }
}
