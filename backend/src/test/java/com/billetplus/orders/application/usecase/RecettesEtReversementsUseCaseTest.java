package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.RecettesEvenement;
import com.billetplus.orders.domain.Reversement;
import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecettesEtReversementsUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-06T10:00:00Z");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 6);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ReversementRepository reversementRepository;
    @Mock
    private StatutEvenements statutEvenements;

    private final UUID evenement = UUID.randomUUID();
    private final UUID organisateur = UUID.randomUUID();

    private ConsulterRecettesUseCase consulter() {
        return new ConsulterRecettesUseCase(orderRepository, reversementRepository, statutEvenements);
    }

    /** Organisateur vérifié, un billet scanné (ADR 0012) : les conditions du virement ont leurs propres tests. */
    private final com.billetplus.auth.api.publique.Comptes comptes = org.mockito.Mockito.mock(
        com.billetplus.auth.api.publique.Comptes.class,
        i -> i.getMethod().getReturnType() == boolean.class ? Boolean.TRUE : java.util.Optional.empty());
    private final com.billetplus.tickets.api.publique.ControleEntrees controle = id -> 1;

    private EnregistrerReversementUseCase enregistrer() {
        org.mockito.Mockito.lenient().when(statutEvenements.organisateurDe(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(organisateur));
        org.mockito.Mockito.lenient().when(statutEvenements.finDe(org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.Optional.of(MAINTENANT.minus(java.time.Duration.ofDays(2))));
        return new EnregistrerReversementUseCase(orderRepository, reversementRepository, statutEvenements, comptes,
            controle, org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class),
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    void l_organisateur_voit_ses_recettes_calculees_en_base() {
        when(statutEvenements.estOrganisateur(evenement, organisateur)).thenReturn(true);
        when(orderRepository.totauxPayees(evenement)).thenReturn(new OrderRepository.TotauxPayees(evenement, 2, 30_000, 1_500));
        when(reversementRepository.totalReverse(evenement)).thenReturn(5_000L);

        RecettesEvenement recettes = consulter().execute(evenement, organisateur);

        assertThat(recettes.brut()).isEqualTo(Money.ofFcfa(30_000));
        assertThat(recettes.commission()).isEqualTo(Money.ofFcfa(1_500));
        assertThat(recettes.net()).isEqualTo(Money.ofFcfa(28_500));
        assertThat(recettes.resteAReverserFcfa()).isEqualTo(23_500);
    }

    @Test
    void un_autre_que_l_organisateur_recoit_le_404_d_un_evenement_inconnu() {
        when(statutEvenements.estOrganisateur(evenement, organisateur)).thenReturn(false);

        assertThatThrownBy(() -> consulter().execute(evenement, organisateur)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(orderRepository, reversementRepository);
    }

    @Test
    void la_liste_reunit_ventes_et_reversements_et_met_le_plus_grand_reste_en_tete() {
        UUID petit = UUID.randomUUID();
        UUID grand = UUID.randomUUID();
        UUID seulementReverse = UUID.randomUUID();
        when(orderRepository.totauxPayeesParEvenement()).thenReturn(List.of(
            new OrderRepository.TotauxPayees(petit, 1, 1_000, 50),
            new OrderRepository.TotauxPayees(grand, 1, 100_000, 5_000)));
        when(reversementRepository.totauxParEvenement()).thenReturn(Map.of(seulementReverse, 1_000L));
        lenient().when(statutEvenements.estAnnule(seulementReverse)).thenReturn(true);

        List<ListerReversementsUseCase.LigneReversement> lignes =
            new ListerReversementsUseCase(orderRepository, reversementRepository, statutEvenements, comptes, controle)
                .execute();

        assertThat(lignes).extracting(ListerReversementsUseCase.LigneReversement::eventId)
            .containsExactly(grand, petit, seulementReverse);
        assertThat(lignes.get(0).recettes().resteAReverserFcfa()).isEqualTo(95_000);
        assertThat(lignes.get(2).evenementAnnule()).isTrue();
        assertThat(lignes.get(2).recettes().resteAReverserFcfa()).isEqualTo(-1_000);
    }

    @Test
    void un_reversement_est_enregistre_pour_un_evenement_qui_a_des_ventes() {
        UUID admin = UUID.randomUUID();
        when(orderRepository.existeCommandePayee(evenement)).thenReturn(true);
        when(reversementRepository.save(any(Reversement.class))).thenAnswer(inv -> inv.getArgument(0));

        Reversement r = enregistrer().execute(evenement, 28_500, "WAVE-1", null, JOUR, admin);

        assertThat(r.getEnregistrePar()).isEqualTo(admin);
        assertThat(r.getEnregistreLe()).isEqualTo(MAINTENANT);
    }

    /** Événement dont les reversements se compensent (+100 puis -100) : toujours connu. */
    @Test
    void un_evenement_qui_n_a_que_des_reversements_reste_connu() {
        when(orderRepository.existeCommandePayee(evenement)).thenReturn(false);
        when(reversementRepository.existePour(evenement)).thenReturn(true);
        when(reversementRepository.save(any(Reversement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(enregistrer().execute(evenement, -100, "CORR-2", "Annule CORR-1", JOUR, UUID.randomUUID())
            .getMontantFcfa()).isEqualTo(-100);
    }

    @Test
    void un_evenement_sans_vente_ni_reversement_est_inconnu() {
        when(orderRepository.existeCommandePayee(evenement)).thenReturn(false);
        when(reversementRepository.existePour(evenement)).thenReturn(false);

        assertThatThrownBy(() -> enregistrer().execute(evenement, 1_000, "R", null, JOUR, UUID.randomUUID()))
            .isInstanceOf(NoSuchElementException.class);
        verify(reversementRepository, never()).save(any());
    }

    @Test
    void aucun_virement_positif_vers_un_evenement_annule_mais_une_correction_reste_possible() {
        when(orderRepository.existeCommandePayee(evenement)).thenReturn(true);
        lenient().when(statutEvenements.estAnnule(evenement)).thenReturn(true);
        lenient().when(reversementRepository.save(any(Reversement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> enregistrer().execute(evenement, 1_000, "R", null, JOUR, UUID.randomUUID()))
            .isInstanceOf(BusinessConflictException.class);
        assertThat(enregistrer().execute(evenement, -1_000, "CORR", "Trop versé avant l'annulation", JOUR,
            UUID.randomUUID()).getMontantFcfa()).isEqualTo(-1_000);
    }
}
