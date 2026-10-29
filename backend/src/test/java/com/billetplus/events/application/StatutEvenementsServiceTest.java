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
    @Mock
    private AffectationAgentRepository affectationRepository;

    private StatutEvenementsService service() {
        return new StatutEvenementsService(eventRepository, affectationRepository);
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

    @Test
    void reconnait_l_organisateur_et_lui_seul() {
        UUID organisateur = UUID.randomUUID();
        Event event = new Event("Concert", null, Instant.now(), null,
            new Venue("Grand Theatre", "Dakar", null, null), organisateur);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThat(service().estOrganisateur(event.getId(), organisateur)).isTrue();
        assertThat(service().estOrganisateur(event.getId(), UUID.randomUUID())).isFalse();
        assertThat(service().estOrganisateur(event.getId(), null)).isFalse();
    }

    @Test
    void un_evenement_inconnu_n_a_pas_d_organisateur() {
        UUID inconnu = UUID.randomUUID();
        when(eventRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThat(service().estOrganisateur(inconnu, UUID.randomUUID())).isFalse();
        assertThat(service().accepteDesCategories(inconnu)).isFalse();
    }

    /** La billetterie se configure en brouillon et une fois publié, plus après annulation. */
    @Test
    void accepte_des_categories_en_brouillon_et_publie_mais_pas_annule() {
        Event brouillon = evenement();
        Event publie = evenement();
        publie.publier();
        Event annule = evenement();
        annule.annuler();
        for (Event e : new Event[]{brouillon, publie, annule}) {
            when(eventRepository.findById(e.getId())).thenReturn(Optional.of(e));
        }

        assertThat(service().accepteDesCategories(brouillon.getId())).isTrue();
        assertThat(service().accepteDesCategories(publie.getId())).isTrue();
        assertThat(service().accepteDesCategories(annule.getId())).isFalse();
    }

    @Test
    void sait_si_un_agent_est_affecte_au_controle_d_un_evenement() {
        UUID eventId = UUID.randomUUID();
        UUID affecte = UUID.randomUUID();
        when(affectationRepository.existe(eventId, affecte)).thenReturn(true);

        assertThat(service().estAgentAffecte(eventId, affecte)).isTrue();
        assertThat(service().estAgentAffecte(eventId, UUID.randomUUID())).isFalse();
        assertThat(service().estAgentAffecte(null, affecte)).isFalse();
        assertThat(service().estAgentAffecte(eventId, null)).isFalse();
    }
}
