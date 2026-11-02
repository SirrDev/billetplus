package com.billetplus.events.application;

import com.billetplus.events.domain.CategorieEvenement;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RechercheEvenementsServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-01T10:00:00Z");

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final RechercheEvenementsService service =
        new RechercheEvenementsService(eventRepository, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    @Test
    void la_recherche_transmet_l_heure_de_l_horloge_et_les_parametres() {
        PageEvenements attendue = new PageEvenements(List.of(), 2, 10, 0);
        when(eventRepository.rechercherPublies("jazz", null, MAINTENANT, 2, 10)).thenReturn(attendue);

        assertThat(service.rechercher("jazz", 2, 10)).isSameAs(attendue);
    }

    @Test
    void la_categorie_est_transmise_au_filtre() {
        PageEvenements attendue = new PageEvenements(List.of(), 0, 10, 0);
        when(eventRepository.rechercherPublies(null, CategorieEvenement.SPORT, MAINTENANT, 0, 10)).thenReturn(attendue);

        assertThat(service.rechercher(null, CategorieEvenement.SPORT, 0, 10)).isSameAs(attendue);
    }

    @Test
    void les_bornes_incluses_sont_acceptees() {
        when(eventRepository.rechercherPublies(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageEvenements(List.of(), 0, 1, 0));

        service.rechercher(null, 0, 1);
        service.rechercher("a".repeat(RechercheEvenementsService.RECHERCHE_LONGUEUR_MAX), 0,
            RechercheEvenementsService.TAILLE_MAX);

        verify(eventRepository).rechercherPublies(null, null, MAINTENANT, 0, 1);
    }

    @Test
    void une_page_negative_est_refusee_sans_interroger_la_base() {
        assertThatThrownBy(() -> service.rechercher(null, -1, 20))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void une_taille_nulle_ou_superieure_au_maximum_est_refusee() {
        assertThatThrownBy(() -> service.rechercher(null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.rechercher(null, 0, RechercheEvenementsService.TAILLE_MAX + 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("taille doit être comprise entre 1 et 50");
        verifyNoInteractions(eventRepository);
    }

    @Test
    void une_recherche_trop_longue_est_refusee() {
        String tropLong = "a".repeat(RechercheEvenementsService.RECHERCHE_LONGUEUR_MAX + 1);
        assertThatThrownBy(() -> service.rechercher(tropLong, 0, 20))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventRepository);
    }
}
