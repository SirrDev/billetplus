package com.billetplus.orders.application.usecase;

import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.orders.application.port.DelaiAttenteVerrous;
import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.MotifSansBillet;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.OrderStatus;
import com.billetplus.orders.domain.Paiement;
import com.billetplus.shared.Money;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.api.publique.EmissionBillets;
import com.billetplus.tickets.api.publique.PlacesEnVente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le dépôt et le gestionnaire de transactions sont de faux objets en mémoire : un
 * mock ne sait pas annuler une transaction, or c'est précisément ce qu'il faut
 * vérifier (un échec d'émission ne laisse pas la commande PAYEE).
 */
class ConfirmerPaiementUseCaseTest {

    /** Dépôt en mémoire : chaque lecture rend une copie, comme une relecture en base. */
    static final class DepotEnMemoire implements OrderRepository {
        private Map<UUID, Order> commandes = new HashMap<>();
        private Map<UUID, Order> copie;

        static Order copier(Order o) {
            return Order.reconstituer(o.getId(), o.getEventId(), o.getTelephone(), o.getLignes(), o.getEmpreinteJeton(),
                o.getCreeLe(), o.getExpireLe(), o.getTauxCommission(), o.getStatus(), o.getPayeeLe(), o.getMotifSansBillet(), o.getPaiement(), o.getTarifEntreeLibre());
        }

        void debuter() {
            copie = new HashMap<>(commandes);
        }

        void annuler() {
            commandes = copie;
        }

        @Override
        public Optional<Order> findById(UUID id) {
            return Optional.ofNullable(commandes.get(id)).map(DepotEnMemoire::copier);
        }

        @Override
        public Order save(Order order) {
            commandes.put(order.getId(), copier(order));
            return order;
        }

        @Override
        public long compterEnAttente(NumeroTelephone telephone, UUID eventId, Instant maintenant) {
            return 0;
        }

        @Override
        public long compterBilletsGratuits(NumeroTelephone telephone, UUID eventId) {
            return 0;
        }

        @Override
        public Optional<Instant> dernierePayee(UUID eventId) {
            return Optional.empty();
        }

        @Override
        public Optional<Order> findByReferencePaiement(String reference) {
            return commandes.values().stream()
                .filter(o -> o.getPaiement() != null && o.getPaiement().reference().equals(reference))
                .findFirst().map(DepotEnMemoire::copier);
        }

        @Override
        public List<Order> findPayeesSansBillet(int limite) {
            return commandes.values().stream().filter(o -> o.getStatus() == OrderStatus.PAYEE_SANS_BILLET).toList();
        }

        @Override
        public List<UUID> evenementsAvecCommandesPayees() {
            return List.of();
        }

        @Override
        public List<Order> findPayeesDeLEvenement(UUID eventId, int limite) {
            return List.of();
        }

        // Recettes (ADR 0009) : sans objet pour la confirmation du paiement.
        @Override
        public TotauxPayees totauxPayees(UUID eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TotauxPayees> totauxPayeesParEvenement() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existeCommandePayee(UUID eventId) {
            throw new UnsupportedOperationException();
        }
    }

    /** Gestionnaire de transactions qui restaure le dépôt en cas d'annulation. */
    static final class TransactionsEnMemoire implements PlatformTransactionManager {
        private final DepotEnMemoire depot;
        int ouvertes;

        TransactionsEnMemoire(DepotEnMemoire depot) {
            this.depot = depot;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            ouvertes++;
            depot.debuter();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
            depot.annuler();
        }
    }

    private static final Instant CREATION = Instant.parse("2026-11-01T10:00:00Z");
    private static final UUID PREMIERE = new UUID(1, 1);
    private static final UUID SECONDE = new UUID(2, 1);
    private static final String REFERENCE = "wave_66a1b2_1716540000";

    private final DepotEnMemoire depot = new DepotEnMemoire();
    private final TransactionsEnMemoire transactions = new TransactionsEnMemoire(depot);
    private final EmissionBillets emission = mock(EmissionBillets.class);
    private final PlacesEnVente places = mock(PlacesEnVente.class);
    private final DelaiAttenteVerrous delai = mock(DelaiAttenteVerrous.class);
    /** Aucune recharge ne porte les références de ces tests (ADR 0010). */
    private final ConfirmerRechargeUseCase recharges =
        mock(ConfirmerRechargeUseCase.class, invocation -> ConfirmerRechargeUseCase.Resultat.INCONNUE);
    private Order commande;

    private ConfirmerPaiementUseCase useCaseA(Instant maintenant) {
        return new ConfirmerPaiementUseCase(depot, emission, places, delai, transactions,
            Clock.fixed(maintenant, ZoneOffset.UTC), recharges);
    }

    @BeforeEach
    void commandeAvecPaiementOuvert() {
        // Dans le désordre : l'émission doit suivre l'ordre des categoryId.
        commande = Order.creer(UUID.randomUUID(), NumeroTelephone.depuis("771234567"),
            List.of(new OrderLine(SECONDE, 1, Money.ofFcfa(15000)), new OrderLine(PREMIERE, 2, Money.ofFcfa(5000))),
            CREATION, Duration.ofMinutes(4), TauxCommission.CINQ_POUR_CENT).commande();
        commande.ouvrirPaiement(new Paiement(MoyenPaiement.WAVE, REFERENCE, 18, "https://pay.wave.com/c/1",
            CREATION.plusSeconds(10)), Duration.ofSeconds(60));
        depot.save(commande);
    }

    private static NotificationPaiement paye(long montant) {
        return new NotificationPaiement(NotificationPaiement.Type.PAYE, REFERENCE, montant);
    }

    private Order relue() {
        return depot.findById(commande.getId()).orElseThrow();
    }

    @Test
    void un_paiement_recu_dans_le_delai_paie_la_commande_et_emet_ses_billets_dans_l_ordre() {
        assertThat(useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.BILLETS_EMIS);

        assertThat(relue().getStatus()).isEqualTo(OrderStatus.PAYEE);
        InOrder ordre = inOrder(delai, emission);
        ordre.verify(delai).appliquer(any());
        ordre.verify(emission).emettre(PREMIERE, commande.getId(), 2);
        ordre.verify(emission).emettre(SECONDE, commande.getId(), 1);
        // IssueTicketUseCase rend lui-même les réservations après validation.
        verify(places, never()).liberer(any(), any());
    }

    @Test
    void un_webhook_rejoue_ne_change_rien() {
        useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000));

        assertThat(useCaseA(CREATION.plusSeconds(70)).traiter(paye(25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.SANS_EFFET);
        verify(emission, times(1)).emettre(eq(PREMIERE), any(), anyInt());
    }

    @Test
    void un_montant_different_est_conserve_a_rembourser_sans_emettre() {
        assertThat(useCaseA(CREATION.plusSeconds(60)).traiter(paye(100)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.A_REMBOURSER);

        assertThat(relue().getStatus()).isEqualTo(OrderStatus.PAYEE_SANS_BILLET);
        assertThat(relue().getMotifSansBillet()).isEqualTo(MotifSansBillet.MONTANT_INCORRECT);
        verify(emission, never()).emettre(any(), any(), anyInt());
        verify(places).liberer(PREMIERE, commande.getId());
        verify(places).liberer(SECONDE, commande.getId());
    }

    @Test
    void un_paiement_apres_le_delai_est_conserve_a_rembourser() {
        assertThat(useCaseA(commande.getExpireLe().plusSeconds(1)).traiter(paye(25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.A_REMBOURSER);
        assertThat(relue().getMotifSansBillet()).isEqualTo(MotifSansBillet.RESERVATION_EXPIREE);
        verify(emission, never()).emettre(any(), any(), anyInt());
    }

    @Test
    void un_paiement_recu_apres_un_echec_est_conserve_a_rembourser() {
        useCaseA(CREATION.plusSeconds(30)).traiter(new NotificationPaiement(NotificationPaiement.Type.ECHOUE, REFERENCE, 25000));
        assertThat(relue().getStatus()).isEqualTo(OrderStatus.ANNULEE);

        assertThat(useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.A_REMBOURSER);
        assertThat(relue().getMotifSansBillet()).isEqualTo(MotifSansBillet.PAIEMENT_APRES_ANNULATION);
    }

    @Test
    void si_l_emission_echoue_vente_fermee_la_commande_n_est_pas_payee_mais_a_rembourser() {
        doThrow(new BusinessConflictException("La vente est fermée pour cet événement"))
            .when(emission).emettre(eq(PREMIERE), any(), anyInt());
        when(places.prixSiEnVente(any(), any())).thenReturn(Optional.empty());

        assertThat(useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.A_REMBOURSER);

        // marquerPayee de la transaction A a été annulé avec elle.
        assertThat(relue().getStatus()).isEqualTo(OrderStatus.PAYEE_SANS_BILLET);
        assertThat(relue().getMotifSansBillet()).isEqualTo(MotifSansBillet.VENTE_FERMEE);
        assertThat(relue().getPayeeLe()).isNotNull();
        verify(places).liberer(PREMIERE, commande.getId());
    }

    @Test
    void si_la_seconde_ligne_manque_de_places_toute_l_emission_est_annulee() {
        doNothing().when(emission).emettre(eq(PREMIERE), any(), anyInt());
        doThrow(new BusinessConflictException("Quota dépassé")).when(emission).emettre(eq(SECONDE), any(), anyInt());
        when(places.prixSiEnVente(any(), any())).thenReturn(Optional.of(Money.ofFcfa(5000)));

        assertThat(useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.A_REMBOURSER);
        assertThat(relue().getMotifSansBillet()).isEqualTo(MotifSansBillet.PLACES_INDISPONIBLES);
    }

    @Test
    void un_conflit_de_version_est_relance_au_plus_trois_fois() {
        doThrow(new OptimisticLockingFailureException("conflit"))
            .doThrow(new OptimisticLockingFailureException("conflit"))
            .doNothing()
            .when(emission).emettre(eq(PREMIERE), any(), anyInt());

        assertThat(useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.BILLETS_EMIS);
        assertThat(transactions.ouvertes).isEqualTo(3);

        DepotEnMemoire autreDepot = new DepotEnMemoire();
        autreDepot.save(DepotEnMemoire.copier(commande));
        doThrow(new OptimisticLockingFailureException("conflit")).when(emission).emettre(any(), any(), anyInt());
        ConfirmerPaiementUseCase toujoursEnConflit = new ConfirmerPaiementUseCase(autreDepot, emission, places, delai,
            new TransactionsEnMemoire(autreDepot), Clock.fixed(CREATION.plusSeconds(60), ZoneOffset.UTC), recharges);
        assertThatThrownBy(() -> toujoursEnConflit.traiter(paye(25000)))
            .isInstanceOf(OptimisticLockingFailureException.class);
        // Rien n'est classé « sans billet » : la passerelle relancera.
        assertThat(autreDepot.findById(commande.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
    }

    @Test
    void une_panne_technique_est_remontee_sans_classer_la_commande() {
        doThrow(new IllegalStateException("Signature Ed25519 impossible"))
            .when(emission).emettre(any(), any(), anyInt());

        assertThatThrownBy(() -> useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(relue().getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
    }

    @Test
    void un_echec_definitif_annule_une_commande_en_attente_et_rend_ses_places() {
        assertThat(useCaseA(CREATION.plusSeconds(30))
                .traiter(new NotificationPaiement(NotificationPaiement.Type.ECHOUE, REFERENCE, 25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.ANNULEE);
        assertThat(relue().getStatus()).isEqualTo(OrderStatus.ANNULEE);
        verify(places).liberer(PREMIERE, commande.getId());
        verify(places).liberer(SECONDE, commande.getId());
    }

    @Test
    void un_echec_tardif_ne_touche_pas_une_commande_payee() {
        useCaseA(CREATION.plusSeconds(60)).traiter(paye(25000));

        assertThat(useCaseA(CREATION.plusSeconds(90))
                .traiter(new NotificationPaiement(NotificationPaiement.Type.ECHOUE, REFERENCE, 25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.SANS_EFFET);
        assertThat(relue().getStatus()).isEqualTo(OrderStatus.PAYEE);
    }

    @Test
    void une_reference_inconnue_ou_un_evenement_sans_effet_ne_change_rien() {
        assertThat(useCaseA(CREATION.plusSeconds(60))
                .traiter(new NotificationPaiement(NotificationPaiement.Type.PAYE, "inconnue", 25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.COMMANDE_INCONNUE);
        assertThat(useCaseA(CREATION.plusSeconds(60))
                .traiter(new NotificationPaiement(NotificationPaiement.Type.AUTRE, REFERENCE, 25000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.SANS_EFFET);
        assertThat(relue().getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_PAIEMENT);
        verify(emission, never()).emettre(any(), any(), anyInt());
    }

    @Test
    void une_reference_sans_commande_mais_avec_recharge_donne_recharge() {
        ConfirmerRechargeUseCase rechargeConnue = mock(ConfirmerRechargeUseCase.class);
        when(rechargeConnue.traiter(any())).thenReturn(ConfirmerRechargeUseCase.Resultat.CREDITEE);
        ConfirmerPaiementUseCase useCase = new ConfirmerPaiementUseCase(depot, emission, places, delai, transactions,
            Clock.fixed(CREATION.plusSeconds(60), ZoneOffset.UTC), rechargeConnue);

        assertThat(useCase.traiter(new NotificationPaiement(NotificationPaiement.Type.PAYE, "ref_recharge", 5000)))
            .isEqualTo(ConfirmerPaiementUseCase.Resultat.RECHARGE);
        verify(emission, never()).emettre(any(), any(), anyInt());
    }
}
