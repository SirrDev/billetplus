package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.orders.domain.exception.TropDeCommandesEnAttenteException;
import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.api.publique.PlacesEnVente;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La synchronisation de transaction est initialisée à la main : le cas d'usage
 * enregistre un afterCompletion, que le test déclenche lui-même pour simuler
 * une validation ou une annulation.
 */
@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-01T10:00:00Z");
    private static final UUID EVENEMENT = UUID.randomUUID();
    /** Ordre connu : PREMIERE < SECONDE selon UUID.compareTo. */
    private static final UUID PREMIERE = new UUID(1, 1);
    private static final UUID SECONDE = new UUID(2, 1);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PlacesEnVente placesEnVente;

    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderUseCase(orderRepository, placesEnVente, Clock.fixed(MAINTENANT, ZoneOffset.UTC),
            Duration.ofMinutes(4), Duration.ofMinutes(5), 3);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void prix(UUID categorie, long fcfa) {
        when(placesEnVente.prixSiEnVente(EVENEMENT, categorie)).thenReturn(Optional.of(Money.ofFcfa(fcfa)));
    }

    private void enregistrementRenvoieLaCommande() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static void terminerLaTransaction(int statut) {
        for (TransactionSynchronization synchronisation : TransactionSynchronizationManager.getSynchronizations()) {
            synchronisation.afterCompletion(statut);
        }
    }

    private Order.CommandeCreee commanderLesDeux() {
        // Volontairement dans le désordre : la réservation doit suivre l'ordre des categoryId.
        return useCase.execute(EVENEMENT, "77 123 45 67", List.of(
            new CreateOrderUseCase.LigneDemandee(SECONDE, 1),
            new CreateOrderUseCase.LigneDemandee(PREMIERE, 2)));
    }

    @Test
    void la_commande_est_enregistree_avec_les_prix_du_serveur_puis_ses_places_reservees_dans_l_ordre() {
        prix(PREMIERE, 5000);
        prix(SECONDE, 15000);
        enregistrementRenvoieLaCommande();

        Order.CommandeCreee creee = commanderLesDeux();
        Order commande = creee.commande();

        assertThat(commande.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
        assertThat(commande.montantTotal()).isEqualTo(Money.ofFcfa(25000));
        assertThat(commande.getTelephone().valeur()).isEqualTo("+221771234567");
        assertThat(commande.getExpireLe()).isEqualTo(MAINTENANT.plus(Duration.ofMinutes(4)));
        assertThat(commande.accepteLeJeton(creee.jeton().enClair())).isTrue();

        InOrder ordre = inOrder(orderRepository, placesEnVente);
        ordre.verify(orderRepository).save(any(Order.class));
        ordre.verify(placesEnVente).reserver(PREMIERE, commande.getId(), 2);
        ordre.verify(placesEnVente).reserver(SECONDE, commande.getId(), 1);

        // Transaction validée : rien n'est rendu.
        terminerLaTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(placesEnVente, never()).liberer(any(), any());
    }

    @Test
    void si_une_reservation_echoue_les_places_deja_reservees_sont_rendues_apres_l_annulation() {
        prix(PREMIERE, 5000);
        prix(SECONDE, 15000);
        enregistrementRenvoieLaCommande();
        when(placesEnVente.reserver(eq(PREMIERE), any(), anyInt())).thenReturn(MAINTENANT.plusSeconds(300));
        when(placesEnVente.reserver(eq(SECONDE), any(), anyInt()))
            .thenThrow(new BusinessConflictException("Places insuffisantes"));

        assertThatThrownBy(this::commanderLesDeux).isInstanceOf(BusinessConflictException.class);
        // Rien n'est rendu tant que la transaction n'est pas terminée.
        verify(placesEnVente, never()).liberer(any(), any());

        terminerLaTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(placesEnVente).liberer(eq(PREMIERE), any());
        verify(placesEnVente, never()).liberer(eq(SECONDE), any());
    }

    @Test
    void un_echec_de_liberation_n_empeche_pas_de_rendre_les_autres_places() {
        UUID troisieme = new UUID(3, 1);
        prix(PREMIERE, 5000);
        prix(SECONDE, 5000);
        prix(troisieme, 5000);
        enregistrementRenvoieLaCommande();
        when(placesEnVente.reserver(eq(PREMIERE), any(), anyInt())).thenReturn(MAINTENANT.plusSeconds(300));
        when(placesEnVente.reserver(eq(SECONDE), any(), anyInt())).thenReturn(MAINTENANT.plusSeconds(300));
        when(placesEnVente.reserver(eq(troisieme), any(), anyInt()))
            .thenThrow(new BusinessConflictException("Places insuffisantes"));
        doThrow(new RuntimeException("Redis indisponible")).when(placesEnVente).liberer(eq(PREMIERE), any());
        doNothing().when(placesEnVente).liberer(eq(SECONDE), any());

        assertThatThrownBy(() -> useCase.execute(EVENEMENT, "771234567", List.of(
            new CreateOrderUseCase.LigneDemandee(PREMIERE, 1),
            new CreateOrderUseCase.LigneDemandee(SECONDE, 1),
            new CreateOrderUseCase.LigneDemandee(troisieme, 1)))).isInstanceOf(BusinessConflictException.class);

        terminerLaTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(placesEnVente).liberer(eq(SECONDE), any());
    }

    @Test
    void une_categorie_inconnue_ou_hors_vente_repond_404_sans_rien_enregistrer() {
        // SECONDE est demandée en premier : son refus arrête la lecture des prix.
        when(placesEnVente.prixSiEnVente(EVENEMENT, SECONDE)).thenReturn(Optional.empty());

        assertThatThrownBy(this::commanderLesDeux)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Catégorie de billet introuvable : " + SECONDE);
        verify(orderRepository, never()).save(any());
        verify(placesEnVente, never()).reserver(any(), any(), anyInt());
    }

    @Test
    void au_dela_de_la_limite_par_telephone_la_commande_est_refusee() {
        prix(PREMIERE, 5000);
        prix(SECONDE, 15000);
        when(orderRepository.compterEnAttente(NumeroTelephone.depuis("771234567"), EVENEMENT, MAINTENANT))
            .thenReturn(3L);

        assertThatThrownBy(this::commanderLesDeux).isInstanceOf(TropDeCommandesEnAttenteException.class);
        verify(orderRepository, never()).save(any());
        verify(placesEnVente, never()).reserver(any(), any(), anyInt());
    }

    @Test
    void sous_la_limite_par_telephone_la_commande_passe() {
        prix(PREMIERE, 5000);
        prix(SECONDE, 15000);
        enregistrementRenvoieLaCommande();
        when(orderRepository.compterEnAttente(any(), any(), any())).thenReturn(2L);

        assertThat(commanderLesDeux().commande()).isNotNull();
    }

    @Test
    void les_entrees_invalides_sont_refusees_avant_toute_lecture() {
        List<CreateOrderUseCase.LigneDemandee> une = List.of(new CreateOrderUseCase.LigneDemandee(PREMIERE, 1));
        assertThatThrownBy(() -> useCase.execute(null, "771234567", une)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(EVENEMENT, "338234567", une)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(EVENEMENT, "771234567", List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        List<CreateOrderUseCase.LigneDemandee> tropDeLignes = IntStream.range(0, Order.LIGNES_MAX + 1)
            .mapToObj(i -> new CreateOrderUseCase.LigneDemandee(UUID.randomUUID(), 1)).toList();
        assertThatThrownBy(() -> useCase.execute(EVENEMENT, "771234567", tropDeLignes))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(placesEnVente, orderRepository);
    }

    @Test
    void un_delai_de_paiement_qui_n_est_pas_plus_court_que_la_reservation_empeche_le_demarrage() {
        Clock horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        assertThatThrownBy(() -> new CreateOrderUseCase(orderRepository, placesEnVente, horloge,
                Duration.ofMinutes(5), Duration.ofMinutes(5), 3))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CreateOrderUseCase(orderRepository, placesEnVente, horloge,
                Duration.ZERO, Duration.ofMinutes(5), 3))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CreateOrderUseCase(orderRepository, placesEnVente, horloge,
                Duration.ofMinutes(4), Duration.ofMinutes(5), 0))
            .isInstanceOf(IllegalStateException.class);
    }
}
