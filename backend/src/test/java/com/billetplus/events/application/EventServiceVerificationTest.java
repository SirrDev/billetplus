package com.billetplus.events.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.EventStatus;
import com.billetplus.events.domain.Venue;
import com.billetplus.events.domain.VillesDeTest;
import com.billetplus.shared.evenements.EvenementAnnule;
import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceVerificationTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private Comptes comptes;

    private final UUID organisateurId = UUID.randomUUID();
    private EventService service;

    @BeforeEach
    void preparer() {
        service = new EventService(eventRepository, venueRepository, publisher, comptes);
        lenient().when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Event evenement() {
        Venue venue = new Venue("Grand Theatre", "Dakar", null, null, VillesDeTest.dakar());
        Event event = new Event("Concert", null, Instant.parse("2026-12-01T20:00:00Z"), null, venue, organisateurId);
        lenient().when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void publier_est_refuse_si_le_compte_n_est_pas_verifie() {
        Event event = evenement();
        when(comptes.estOrganisateurVerifie(organisateurId)).thenReturn(false);

        assertThatThrownBy(() -> service.publier(event.getId(), organisateurId))
            .isInstanceOf(BusinessConflictException.class)
            .hasMessage("Votre compte doit être vérifié avant de publier");

        assertThat(event.getStatus()).isEqualTo(EventStatus.BROUILLON);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void publier_est_accepte_si_le_compte_est_verifie() {
        Event event = evenement();
        when(comptes.estOrganisateurVerifie(organisateurId)).thenReturn(true);

        Event publie = service.publier(event.getId(), organisateurId);

        assertThat(publie.getStatus()).isEqualTo(EventStatus.PUBLIE);
        verify(eventRepository).save(event);
    }

    @Test
    void suspendre_un_evenement_l_annule_et_publie_evenement_annule() {
        Event event = evenement();

        Event suspendu = service.suspendre(event.getId(), "Faux evenement");

        assertThat(suspendu.getStatus()).isEqualTo(EventStatus.ANNULE);
        assertThat(suspendu.getSuspenduMotif()).isEqualTo("Faux evenement");
        verify(publisher).publishEvent(new EvenementAnnule(event.getId()));
    }

    @Test
    void suspendre_avec_un_motif_vide_ne_sauvegarde_ni_ne_publie() {
        Event event = evenement();

        assertThatThrownBy(() -> service.suspendre(event.getId(), " ")).isInstanceOf(IllegalArgumentException.class);

        verify(eventRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void suspendre_tous_les_evenements_ne_touche_que_les_brouillons_et_les_publies() {
        Event brouillon = evenement();
        Event publie = evenement();
        publie.publier();
        Event dejaAnnule = evenement();
        dejaAnnule.annuler();
        when(eventRepository.findByOrganizerId(organisateurId)).thenReturn(List.of(brouillon, publie, dejaAnnule));

        int suspendus = service.suspendreTousLesEvenementsDe(organisateurId);

        assertThat(suspendus).isEqualTo(2);
        assertThat(brouillon.getStatus()).isEqualTo(EventStatus.ANNULE);
        assertThat(brouillon.getSuspenduMotif()).isNotBlank();
        assertThat(publie.getStatus()).isEqualTo(EventStatus.ANNULE);
        assertThat(dejaAnnule.getSuspenduMotif()).isNull();
        verify(publisher).publishEvent(new EvenementAnnule(brouillon.getId()));
        verify(publisher).publishEvent(new EvenementAnnule(publie.getId()));
        verify(publisher, never()).publishEvent(new EvenementAnnule(dejaAnnule.getId()));
        verify(publisher, times(2)).publishEvent(any(EvenementAnnule.class));
    }

    @Test
    void suspendre_tous_les_evenements_d_un_organisateur_sans_evenement_ne_publie_rien() {
        when(eventRepository.findByOrganizerId(organisateurId)).thenReturn(List.of());

        assertThat(service.suspendreTousLesEvenementsDe(organisateurId)).isZero();

        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
