package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.PasserellePaiement;
import com.billetplus.orders.application.port.RechargeRepository;
import com.billetplus.orders.domain.MoyenPaiement;
import com.billetplus.orders.domain.RechargeCredit;
import com.billetplus.orders.domain.StatutRecharge;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OuvrirRechargeUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-06T10:00:00Z");
    private static final UUID ORGANISATEUR = UUID.randomUUID();

    private final RechargeRepository recharges = mock(RechargeRepository.class);
    private final PasserellePaiement passerelle = mock(PasserellePaiement.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final Clock clock = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    private OuvrirRechargeUseCase useCase;
    private RechargeCredit enregistree;

    @BeforeEach
    void preparer() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(recharges.save(any(RechargeCredit.class))).thenAnswer(i -> {
            enregistree = i.getArgument(0);
            return enregistree;
        });
        when(recharges.findById(any())).thenAnswer(i -> Optional.ofNullable(enregistree));
        when(passerelle.ouvrir(any())).thenReturn(
            new PasserellePaiement.PaiementCree("ref_r1", 42, "https://pay.example.com/r1"));
        useCase = new OuvrirRechargeUseCase(recharges, passerelle, transactions, clock, 1000, 1_000_000);
    }

    @Test
    void la_demande_envoyee_a_la_passerelle_porte_le_retour_recharge_l_id_et_le_montant() {
        OuvrirRechargeUseCase.LienRecharge lien = useCase.execute(ORGANISATEUR, 5000, MoyenPaiement.WAVE, "771234567");

        ArgumentCaptor<PasserellePaiement.DemandePaiement> demande =
            ArgumentCaptor.forClass(PasserellePaiement.DemandePaiement.class);
        verify(passerelle).ouvrir(demande.capture());
        assertThat(demande.getValue().retour()).isEqualTo(PasserellePaiement.Retour.RECHARGE);
        assertThat(demande.getValue().orderId()).isEqualTo(lien.rechargeId());
        assertThat(demande.getValue().montant()).isEqualTo(Money.ofFcfa(5000));
        assertThat(demande.getValue().moyen()).isEqualTo(MoyenPaiement.WAVE);
        assertThat(lien.url()).isEqualTo("https://pay.example.com/r1");
        assertThat(lien.moyen()).isEqualTo(MoyenPaiement.WAVE);
    }

    @Test
    void le_paiement_ouvert_est_enregistre_sur_la_recharge_en_attente() {
        useCase.execute(ORGANISATEUR, 5000, MoyenPaiement.ORANGE_MONEY, "771234567");

        verify(recharges, times(2)).save(any(RechargeCredit.class));
        assertThat(enregistree.getPaiement().reference()).isEqualTo("ref_r1");
        assertThat(enregistree.getStatus()).isEqualTo(StatutRecharge.EN_ATTENTE);
        assertThat(enregistree.getOrganisateurId()).isEqualTo(ORGANISATEUR);
    }

    @Test
    void un_montant_sous_le_minimum_est_refuse_sans_appeler_la_passerelle() {
        assertThatThrownBy(() -> useCase.execute(ORGANISATEUR, 999, MoyenPaiement.WAVE, "771234567"))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(passerelle);
        verify(recharges, never()).save(any());
    }

    @Test
    void un_montant_au_dessus_du_maximum_est_refuse_sans_appeler_la_passerelle() {
        assertThatThrownBy(() -> useCase.execute(ORGANISATEUR, 1_000_001, MoyenPaiement.WAVE, "771234567"))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(passerelle);
        verify(recharges, never()).save(any());
    }

    @Test
    void un_moyen_de_paiement_absent_est_refuse_sans_appeler_la_passerelle() {
        assertThatThrownBy(() -> useCase.execute(ORGANISATEUR, 5000, null, "771234567"))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(passerelle);
    }

    @Test
    void un_telephone_invalide_est_refuse_sans_appeler_la_passerelle() {
        assertThatThrownBy(() -> useCase.execute(ORGANISATEUR, 5000, MoyenPaiement.WAVE, "12345"))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(passerelle);
    }

    @Test
    void un_minimum_superieur_au_maximum_est_refuse_au_demarrage() {
        assertThatThrownBy(() -> new OuvrirRechargeUseCase(recharges, passerelle, transactions, clock, 5000, 1000))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void un_minimum_nul_est_refuse_au_demarrage() {
        assertThatThrownBy(() -> new OuvrirRechargeUseCase(recharges, passerelle, transactions, clock, 0, 1000))
            .isInstanceOf(IllegalStateException.class);
    }
}
