package com.billetplus.orders.api.dto;

import com.billetplus.orders.application.usecase.ConsulterCreditUseCase;
import com.billetplus.orders.domain.MouvementCredit;
import com.billetplus.orders.domain.RechargeCredit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crédit prépayé d'un organisateur (ADR 0010), tel qu'il le voit : solde, tarifs en
 * vigueur, derniers mouvements et dernières recharges. Sans lien de paiement ni numéro.
 */
public record CreditDto(long soldeFcfa, Tarif tarif, long rechargeMinFcfa, long rechargeMaxFcfa,
                        List<Mouvement> mouvements, List<Recharge> recharges) {

    public record Tarif(int seuilPlaces, int tarifPetitFcfa, int tarifGrandFcfa) {
    }

    public record Mouvement(UUID id, String type, long montantFcfa, UUID orderId, Instant creeLe) {
        static Mouvement from(MouvementCredit m) {
            return new Mouvement(m.id(), m.type().name(), m.montantFcfa(), m.orderId(), m.creeLe());
        }
    }

    public record Recharge(UUID id, long montantFcfa, String status, String moyen, Instant creeLe, Instant traiteeLe) {
        static Recharge from(RechargeCredit r) {
            return new Recharge(r.getId(), r.getMontant().valeur().longValueExact(), r.getStatus().name(),
                r.getPaiement() == null ? null : r.getPaiement().moyen().name(), r.getCreeLe(), r.getTraiteeLe());
        }
    }

    public static CreditDto from(ConsulterCreditUseCase.Credit credit) {
        return new CreditDto(credit.solde().valeur().longValueExact(),
            new Tarif(credit.tarif().seuilPlaces(), credit.tarif().tarifPetitFcfa(), credit.tarif().tarifGrandFcfa()),
            credit.rechargeMinFcfa(), credit.rechargeMaxFcfa(),
            credit.mouvements().stream().map(Mouvement::from).toList(),
            credit.recharges().stream().map(Recharge::from).toList());
    }
}
