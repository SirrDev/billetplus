package com.billetplus.orders.application.usecase;

import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.Paiement;
import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.shared.Money;
import com.billetplus.shared.exception.ServiceExterneIndisponibleException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OuvrirPaiementUseCaseTest {

    private static final Instant CREATION = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration MARGE = Duration.ofSeconds(60);

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PasserellePaiement passerelle = mock(PasserellePaiement.class);

    private OuvrirPaiementUseCase useCaseA(Instant maintenant) {
        return new OuvrirPaiementUseCase(new ConsulterCommandeUseCase(orderRepository), orderRepository, passerelle,
            mock(PlatformTransactionManager.class), Clock.fixed(maintenant, ZoneOffset.UTC), MARGE);
    }

    private Order.CommandeCreee commande(long prix) {
        Order.CommandeCreee creee = Order.creer(UUID.randomUUID(), NumeroTelephone.depuis("771234567"),
            List.of(new OrderLine(UUID.randomUUID(), 2, Money.ofFcfa(prix))), CREATION, Duration.ofMinutes(4), TauxCommission.CINQ_POUR_CENT);
        when(orderRepository.findById(creee.commande().getId())).thenReturn(Optional.of(creee.commande()));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        return creee;
    }

    private void passerelleRepond(String reference) {
        when(passerelle.ouvrir(any())).thenReturn(
            new PasserellePaiement.PaiementCree(reference, 18, "https://pay.wave.com/c/" + reference));
    }

    @Test
    void le_paiement_est_ouvert_pour_le_montant_et_le_numero_de_la_commande_puis_enregistre() {
        Order.CommandeCreee creee = commande(5000);
        passerelleRepond("wave_1");

        OuvrirPaiementUseCase.LienPaiement lien = useCaseA(CREATION.plusSeconds(30))
            .execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.WAVE);

        assertThat(lien.url()).isEqualTo("https://pay.wave.com/c/wave_1");
        assertThat(lien.expireLe()).isEqualTo(creee.commande().getExpireLe());
        ArgumentCaptor<PasserellePaiement.DemandePaiement> demande =
            ArgumentCaptor.forClass(PasserellePaiement.DemandePaiement.class);
        verify(passerelle).ouvrir(demande.capture());
        assertThat(demande.getValue().montant()).isEqualTo(Money.ofFcfa(10000));
        assertThat(demande.getValue().telephone().valeur()).isEqualTo("+221771234567");
        assertThat(demande.getValue().moyen()).isEqualTo(MoyenPaiement.WAVE);
        assertThat(creee.commande().getPaiement().reference()).isEqualTo("wave_1");
        verify(orderRepository).save(creee.commande());
    }

    @Test
    void redemander_le_meme_moyen_renvoie_le_meme_lien_sans_rappeler_la_passerelle() {
        Order.CommandeCreee creee = commande(5000);
        creee.commande().ouvrirPaiement(new Paiement(MoyenPaiement.WAVE, "wave_1", 18,
            "https://pay.wave.com/c/wave_1", CREATION.plusSeconds(10)), MARGE);

        OuvrirPaiementUseCase.LienPaiement lien = useCaseA(CREATION.plusSeconds(200))
            .execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.WAVE);

        assertThat(lien.url()).isEqualTo("https://pay.wave.com/c/wave_1");
        verifyNoInteractions(passerelle);
    }

    @Test
    void un_autre_moyen_apres_un_paiement_ouvert_est_refuse() {
        Order.CommandeCreee creee = commande(5000);
        creee.commande().ouvrirPaiement(new Paiement(MoyenPaiement.WAVE, "wave_1", 18,
            "https://pay.wave.com/c/wave_1", CREATION), MARGE);

        assertThatThrownBy(() -> useCaseA(CREATION.plusSeconds(30))
                .execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.ORANGE_MONEY))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        verifyNoInteractions(passerelle);
    }

    @Test
    void a_moins_d_une_minute_de_l_expiration_la_passerelle_n_est_pas_appelee() {
        Order.CommandeCreee creee = commande(5000);

        assertThatThrownBy(() -> useCaseA(CREATION.plus(Duration.ofMinutes(3)).plusSeconds(1))
                .execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.WAVE))
            .isInstanceOf(TransitionCommandeInterditeException.class)
            .hasMessageContaining("presque écoulé");
        verifyNoInteractions(passerelle);
    }

    @Test
    void une_commande_gratuite_ne_passe_pas_par_la_passerelle() {
        Order.CommandeCreee creee = commande(0);

        assertThatThrownBy(() -> useCaseA(CREATION.plusSeconds(30))
                .execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.WAVE))
            .isInstanceOf(TransitionCommandeInterditeException.class)
            .hasMessageContaining("gratuite");
        verifyNoInteractions(passerelle);
    }

    @Test
    void un_jeton_faux_repond_404_sans_appeler_la_passerelle() {
        Order.CommandeCreee creee = commande(5000);

        assertThatThrownBy(() -> useCaseA(CREATION.plusSeconds(30))
                .execute(creee.commande().getId(), "faux", MoyenPaiement.WAVE))
            .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(passerelle);
    }

    @Test
    void une_panne_de_la_passerelle_ne_modifie_pas_la_commande() {
        Order.CommandeCreee creee = commande(5000);
        when(passerelle.ouvrir(any())).thenThrow(new ServiceExterneIndisponibleException("panne"));

        assertThatThrownBy(() -> useCaseA(CREATION.plusSeconds(30))
                .execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.WAVE))
            .isInstanceOf(ServiceExterneIndisponibleException.class);
        assertThat(creee.commande().getPaiement()).isNull();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void un_conflit_a_l_enregistrement_est_remonte() {
        Order.CommandeCreee creee = commande(5000);
        passerelleRepond("wave_1");
        when(orderRepository.save(any(Order.class))).thenThrow(new OptimisticLockingFailureException("conflit"));

        assertThatThrownBy(() -> useCaseA(CREATION.plusSeconds(30))
                .execute(creee.commande().getId(), creee.jeton().enClair(), MoyenPaiement.WAVE))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void le_moyen_de_paiement_est_obligatoire() {
        Order.CommandeCreee creee = commande(5000);
        assertThatThrownBy(() -> useCaseA(CREATION).execute(creee.commande().getId(), creee.jeton().enClair(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
