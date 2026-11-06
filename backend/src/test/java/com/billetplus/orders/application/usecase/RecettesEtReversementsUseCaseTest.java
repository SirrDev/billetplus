package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.RecettesEvenement;
import com.billetplus.orders.domain.Reversement;
import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecettesEtReversementsUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-06T10:00:00Z");

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

    private static Order payee(long prix) {
        Order commande = Order.creer(UUID.randomUUID(), NumeroTelephone.depuis("77 123 45 67"),
            List.of(new OrderLine(UUID.randomUUID(), 1, Money.ofFcfa(prix))), MAINTENANT, Duration.ofMinutes(4),
            TauxCommission.CINQ_POUR_CENT).commande();
        commande.marquerPayee(MAINTENANT.plusSeconds(30));
        return commande;
    }

    @Test
    void l_organisateur_voit_ses_recettes() {
        when(statutEvenements.estOrganisateur(evenement, organisateur)).thenReturn(true);
        when(orderRepository.findPayeesDeLEvenement(evenement, ConsulterRecettesUseCase.LIMITE_COMMANDES))
            .thenReturn(List.of(payee(10_000), payee(20_000)));
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
    void une_lecture_tronquee_est_refusee_plutot_qu_un_montant_faux() {
        List<Order> pleine = java.util.Collections.nCopies(ConsulterRecettesUseCase.LIMITE_COMMANDES, payee(1_000));
        when(orderRepository.findPayeesDeLEvenement(evenement, ConsulterRecettesUseCase.LIMITE_COMMANDES)).thenReturn(pleine);

        assertThatThrownBy(() -> consulter().recettesDe(evenement)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void la_liste_reunit_ventes_et_reversements_et_met_le_plus_grand_reste_en_tete() {
        UUID petit = UUID.randomUUID();
        UUID grand = UUID.randomUUID();
        UUID seulementReverse = UUID.randomUUID();
        when(orderRepository.evenementsAvecCommandesPayees()).thenReturn(List.of(petit, grand));
        when(reversementRepository.totauxParEvenement()).thenReturn(Map.of(seulementReverse, 1_000L));
        when(orderRepository.findPayeesDeLEvenement(petit, ConsulterRecettesUseCase.LIMITE_COMMANDES)).thenReturn(List.of(payee(1_000)));
        when(orderRepository.findPayeesDeLEvenement(grand, ConsulterRecettesUseCase.LIMITE_COMMANDES)).thenReturn(List.of(payee(100_000)));
        when(orderRepository.findPayeesDeLEvenement(seulementReverse, ConsulterRecettesUseCase.LIMITE_COMMANDES)).thenReturn(List.of());
        lenient().when(reversementRepository.totalReverse(seulementReverse)).thenReturn(1_000L);
        // Les deux autres événements ne sont pas annulés (réponse par défaut des simulacres : false).
        lenient().when(statutEvenements.estAnnule(seulementReverse)).thenReturn(true);

        List<ListerReversementsUseCase.LigneReversement> lignes = new ListerReversementsUseCase(orderRepository,
            reversementRepository, statutEvenements, consulter()).execute();

        assertThat(lignes).extracting(ListerReversementsUseCase.LigneReversement::eventId)
            .containsExactly(grand, petit, seulementReverse);
        assertThat(lignes.get(2).evenementAnnule()).isTrue();
        assertThat(lignes.get(2).recettes().resteAReverserFcfa()).isEqualTo(-1_000);
    }

    @Test
    void un_reversement_est_enregistre_pour_un_evenement_qui_a_des_ventes() {
        EnregistrerReversementUseCase useCase = new EnregistrerReversementUseCase(orderRepository, reversementRepository,
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        UUID admin = UUID.randomUUID();
        when(orderRepository.evenementsAvecCommandesPayees()).thenReturn(List.of(evenement));
        when(reversementRepository.save(any(Reversement.class))).thenAnswer(inv -> inv.getArgument(0));

        Reversement r = useCase.execute(evenement, 28_500, "WAVE-1", null, LocalDate.of(2026, 11, 6), admin);

        assertThat(r.getEnregistrePar()).isEqualTo(admin);
        assertThat(r.getEnregistreLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void un_evenement_sans_vente_ni_reversement_est_inconnu() {
        EnregistrerReversementUseCase useCase = new EnregistrerReversementUseCase(orderRepository, reversementRepository,
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        when(orderRepository.evenementsAvecCommandesPayees()).thenReturn(List.of());
        when(reversementRepository.totalReverse(evenement)).thenReturn(0L);

        assertThatThrownBy(() -> useCase.execute(evenement, 1_000, "R", null, LocalDate.of(2026, 11, 6), UUID.randomUUID()))
            .isInstanceOf(NoSuchElementException.class);
        verify(reversementRepository, never()).save(any());
        verify(orderRepository, never()).findPayeesDeLEvenement(any(), anyInt());
    }
}
