package com.billetplus.orders.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.CreditRepository;
import com.billetplus.orders.application.port.DelaiAttenteVerrous;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.api.publique.EmissionBillets;
import com.billetplus.tickets.api.publique.PlacesEnVente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreerEntreeLibreUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-06T10:00:00Z");
    private static final UUID EVENEMENT = UUID.randomUUID();
    private static final UUID ORGANISATEUR = UUID.randomUUID();
    private static final UUID PREMIERE = new UUID(1, 1);
    private static final UUID SECONDE = new UUID(2, 1);
    private static final String TELEPHONE = "771234567";

    private final CreateOrderUseCase createOrder = mock(CreateOrderUseCase.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CreditRepository credit = mock(CreditRepository.class);
    private final PlacesEnVente places = mock(PlacesEnVente.class);
    private final EmissionBillets emission = mock(EmissionBillets.class);
    private final StatutEvenements statuts = mock(StatutEvenements.class);
    private final DelaiAttenteVerrous delai = mock(DelaiAttenteVerrous.class);
    private final Clock clock = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    private final List<CreateOrderUseCase.LigneDemandee> demande = List.of(
        new CreateOrderUseCase.LigneDemandee(SECONDE, 1), new CreateOrderUseCase.LigneDemandee(PREMIERE, 2));
    private Order.CommandeCreee creee;
    private CreerEntreeLibreUseCase useCase;

    private CreerEntreeLibreUseCase construire(int seuil, int petit, int grand, int max) {
        return new CreerEntreeLibreUseCase(createOrder, orders, credit, places, emission, statuts, delai, clock,
            seuil, petit, grand, max);
    }

    @BeforeEach
    void preparer() {
        useCase = construire(100, 75, 50, 10);
        // Dans le desordre : l'emission doit suivre l'ordre croissant des categoryId.
        creee = Order.creer(EVENEMENT, NumeroTelephone.depuis(TELEPHONE),
            List.of(new OrderLine(SECONDE, 1, Money.ofFcfa(0)), new OrderLine(PREMIERE, 2, Money.ofFcfa(0))),
            MAINTENANT, Duration.ofMinutes(4), TauxCommission.CINQ_POUR_CENT);
        when(createOrder.creerPourEntreeLibre(any(), any(), any())).thenReturn(creee);
        when(orders.compterBilletsGratuits(any(), any())).thenReturn(0L);
        when(orders.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(statuts.organisateurDe(EVENEMENT)).thenReturn(Optional.of(ORGANISATEUR));
        when(places.placesGratuites(EVENEMENT)).thenReturn(50L);
        when(credit.debiterEntreeLibre(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void une_entree_libre_reussie_rend_la_commande_payee_avec_son_jeton() {
        Order.CommandeCreee resultat = useCase.execute(EVENEMENT, TELEPHONE, demande);

        assertThat(resultat.commande().getStatus()).isEqualTo(OrderStatus.PAYEE);
        assertThat(resultat.jeton()).isSameAs(creee.jeton());
        verify(delai).appliquer(Duration.ofSeconds(5));
    }

    @Test
    void jusqu_au_seuil_le_petit_tarif_de_75_est_debite_par_billet() {
        when(places.placesGratuites(EVENEMENT)).thenReturn(100L);

        Order.CommandeCreee resultat = useCase.execute(EVENEMENT, TELEPHONE, demande);

        assertThat(resultat.commande().getTarifEntreeLibre()).isEqualTo(Money.ofFcfa(75));
        verify(credit).debiterEntreeLibre(ORGANISATEUR, Money.ofFcfa(225), resultat.commande().getId(), MAINTENANT);
    }

    @Test
    void au_dela_du_seuil_le_grand_tarif_de_50_est_debite_par_billet() {
        when(places.placesGratuites(EVENEMENT)).thenReturn(101L);

        Order.CommandeCreee resultat = useCase.execute(EVENEMENT, TELEPHONE, demande);

        assertThat(resultat.commande().getTarifEntreeLibre()).isEqualTo(Money.ofFcfa(50));
        verify(credit).debiterEntreeLibre(ORGANISATEUR, Money.ofFcfa(150), resultat.commande().getId(), MAINTENANT);
    }

    @Test
    void les_billets_sont_emis_par_ligne_dans_l_ordre_croissant_de_categorie() {
        Order commande = useCase.execute(EVENEMENT, TELEPHONE, demande).commande();

        InOrder ordre = inOrder(credit, emission);
        ordre.verify(credit).debiterEntreeLibre(any(), any(), any(), any());
        ordre.verify(emission).emettre(PREMIERE, commande.getId(), 2);
        ordre.verify(emission).emettre(SECONDE, commande.getId(), 1);
    }

    @Test
    void un_credit_insuffisant_donne_un_conflit_avec_message_generique_et_aucune_emission() {
        when(credit.debiterEntreeLibre(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(EVENEMENT, TELEPHONE, demande))
            .isInstanceOf(BusinessConflictException.class)
            .hasMessage(CreerEntreeLibreUseCase.CREDIT_INSUFFISANT);
        verify(emission, never()).emettre(any(), any(), anyInt());
    }

    @Test
    void le_plafond_par_numero_depasse_donne_un_conflit_sans_debit_ni_emission() {
        // 9 deja obtenus + 3 billets demandes = 12 > 10
        when(orders.compterBilletsGratuits(any(), any())).thenReturn(9L);

        assertThatThrownBy(() -> useCase.execute(EVENEMENT, TELEPHONE, demande))
            .isInstanceOf(BusinessConflictException.class);
        // Seul le verrou du numéro a été pris : aucun débit.
        verify(credit, never()).debiterEntreeLibre(any(), any(), any(), any());
        verifyNoInteractions(emission);
        verify(orders, never()).save(any());
    }

    @Test
    void le_plafond_exactement_atteint_est_accepte() {
        // 7 deja obtenus + 3 billets = 10, pas plus
        when(orders.compterBilletsGratuits(any(), any())).thenReturn(7L);

        assertThat(useCase.execute(EVENEMENT, TELEPHONE, demande).commande().getStatus())
            .isEqualTo(OrderStatus.PAYEE);
    }

    @Test
    void un_organisateur_introuvable_donne_une_absence_sans_debit_ni_emission() {
        when(statuts.organisateurDe(EVENEMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(EVENEMENT, TELEPHONE, demande))
            .isInstanceOf(NoSuchElementException.class);
        // Seul le verrou du numéro a été pris : aucun débit.
        verify(credit, never()).debiterEntreeLibre(any(), any(), any(), any());
        verifyNoInteractions(emission);
    }

    @Test
    void un_plafond_par_numero_inferieur_a_un_est_refuse_au_demarrage() {
        assertThatThrownBy(() -> construire(100, 75, 50, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void un_tarif_nul_est_refuse_au_demarrage() {
        assertThatThrownBy(() -> construire(100, 0, 50, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> construire(100, 75, 0, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_seuil_nul_est_refuse_au_demarrage() {
        assertThatThrownBy(() -> construire(0, 75, 50, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
