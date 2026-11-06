package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.CreditRepository;
import com.billetplus.orders.application.port.RechargeRepository;
import com.billetplus.orders.domain.MouvementCredit;
import com.billetplus.orders.domain.RechargeCredit;
import com.billetplus.orders.domain.TarifEntreeLibre;
import com.billetplus.shared.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Lecture (niveau 1) — le crédit prépayé d'un organisateur (ADR 0010) : solde, derniers
 * mouvements et dernières recharges. L'organisateur vient du jeton : chacun ne voit
 * que son propre crédit.
 */
@Service
public class ConsulterCreditUseCase {

    static final int LIMITE = 50;

    /** @param tarif tarifs en vigueur, affichés à l'organisateur (aucun montant écrit en dur dans la PWA) */
    public record Credit(Money solde, List<MouvementCredit> mouvements, List<RechargeCredit> recharges,
                         TarifEntreeLibre tarif, long rechargeMinFcfa, long rechargeMaxFcfa) {
    }

    private final CreditRepository creditRepository;
    private final RechargeRepository rechargeRepository;
    private final TarifEntreeLibre tarif;
    private final long rechargeMin;
    private final long rechargeMax;

    public ConsulterCreditUseCase(CreditRepository creditRepository, RechargeRepository rechargeRepository,
                                  @Value("${billetplus.entree-libre.seuil-places:100}") int seuilPlaces,
                                  @Value("${billetplus.entree-libre.tarif-petit:75}") int tarifPetit,
                                  @Value("${billetplus.entree-libre.tarif-grand:50}") int tarifGrand,
                                  @Value("${billetplus.credit.recharge-min:1000}") long rechargeMin,
                                  @Value("${billetplus.credit.recharge-max:1000000}") long rechargeMax) {
        this.creditRepository = creditRepository;
        this.rechargeRepository = rechargeRepository;
        this.tarif = new TarifEntreeLibre(seuilPlaces, tarifPetit, tarifGrand);
        this.rechargeMin = rechargeMin;
        this.rechargeMax = rechargeMax;
    }

    public Credit execute(UUID organisateurId) {
        return new Credit(creditRepository.solde(organisateurId),
            creditRepository.derniersMouvements(organisateurId, LIMITE),
            rechargeRepository.dernieres(organisateurId, LIMITE), tarif, rechargeMin, rechargeMax);
    }
}
