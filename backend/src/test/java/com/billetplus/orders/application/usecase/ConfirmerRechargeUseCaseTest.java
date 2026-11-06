package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.CreditRepository;
import com.billetplus.orders.application.port.NotificationPaiement;
import com.billetplus.orders.application.port.RechargeRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.RechargeCredit;
import com.billetplus.orders.domain.StatutRecharge;
import com.billetplus.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConfirmerRechargeUseCaseTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-06T10:00:00Z");
    private static final String REFERENCE = "wave_recharge_1";
    private static final UUID ORGANISATEUR = UUID.randomUUID();

    private final RechargeRepository recharges = mock(RechargeRepository.class);
    private final CreditRepository credit = mock(CreditRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private ConfirmerRechargeUseCase useCase;
    private RechargeCredit recharge;

    @BeforeEach
    void preparer() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        useCase = new ConfirmerRechargeUseCase(recharges, credit, transactions, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        recharge = RechargeCredit.creer(ORGANISATEUR, 5000, NumeroTelephone.depuis("771234567"), 1000, 1_000_000,
            MAINTENANT.minusSeconds(60));
    }

    private void rechargeConnue() {
        when(recharges.findByReferencePaiement(REFERENCE)).thenReturn(Optional.of(recharge));
        when(recharges.findById(recharge.getId())).thenReturn(Optional.of(recharge));
    }

    private static NotificationPaiement notification(NotificationPaiement.Type type, long montant) {
        return new NotificationPaiement(type, REFERENCE, montant);
    }

    @Test
    void une_reference_inconnue_donne_inconnue_sans_rien_ecrire() {
        when(recharges.findByReferencePaiement(REFERENCE)).thenReturn(Optional.empty());

        assertThat(useCase.traiter(notification(NotificationPaiement.Type.PAYE, 5000)))
            .isEqualTo(ConfirmerRechargeUseCase.Resultat.INCONNUE);
        verifyNoInteractions(credit);
        verify(recharges, never()).save(any());
    }

    @Test
    void un_paiement_au_bon_montant_credite_le_solde_une_seule_fois() {
        rechargeConnue();

        assertThat(useCase.traiter(notification(NotificationPaiement.Type.PAYE, 5000)))
            .isEqualTo(ConfirmerRechargeUseCase.Resultat.CREDITEE);

        verify(credit, times(1)).crediterRecharge(ORGANISATEUR, Money.ofFcfa(5000), recharge.getId(), MAINTENANT);
        verify(recharges).save(recharge);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.PAYEE);
    }

    @Test
    void un_montant_different_donne_anomalie_et_ne_credite_jamais() {
        rechargeConnue();

        assertThat(useCase.traiter(notification(NotificationPaiement.Type.PAYE, 100)))
            .isEqualTo(ConfirmerRechargeUseCase.Resultat.ANOMALIE);

        verify(credit, never()).crediterRecharge(any(), any(), any(), any());
        verify(recharges).save(recharge);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.ANOMALIE);
        assertThat(recharge.getMontantRecuFcfa()).isEqualTo(100L);
    }

    @Test
    void un_echec_de_paiement_ferme_la_recharge_sans_credit() {
        rechargeConnue();

        assertThat(useCase.traiter(notification(NotificationPaiement.Type.ECHOUE, 5000)))
            .isEqualTo(ConfirmerRechargeUseCase.Resultat.ECHOUEE);

        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.ECHOUEE);
        verify(recharges).save(recharge);
        verifyNoInteractions(credit);
    }

    @Test
    void un_webhook_rejoue_sur_une_recharge_deja_payee_est_sans_effet() {
        rechargeConnue();
        recharge.confirmer(5000, MAINTENANT.minusSeconds(5));

        assertThat(useCase.traiter(notification(NotificationPaiement.Type.PAYE, 5000)))
            .isEqualTo(ConfirmerRechargeUseCase.Resultat.SANS_EFFET);

        verifyNoInteractions(credit);
        verify(recharges, never()).save(any());
    }

    @Test
    void un_echec_tardif_sur_une_recharge_deja_payee_est_sans_effet() {
        rechargeConnue();
        recharge.confirmer(5000, MAINTENANT.minusSeconds(5));

        assertThat(useCase.traiter(notification(NotificationPaiement.Type.ECHOUE, 5000)))
            .isEqualTo(ConfirmerRechargeUseCase.Resultat.SANS_EFFET);
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.PAYEE);
    }

    @Test
    void un_evenement_de_type_autre_est_sans_effet() {
        when(recharges.findByReferencePaiement(REFERENCE)).thenReturn(Optional.of(recharge));

        assertThat(useCase.traiter(notification(NotificationPaiement.Type.AUTRE, 5000)))
            .isEqualTo(ConfirmerRechargeUseCase.Resultat.SANS_EFFET);

        verifyNoInteractions(credit);
        verify(recharges, never()).save(any());
        assertThat(recharge.getStatus()).isEqualTo(StatutRecharge.EN_ATTENTE);
    }
}
