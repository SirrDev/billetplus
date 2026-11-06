package com.billetplus.orders.api;

import com.billetplus.orders.api.dto.CreditDto;
import com.billetplus.orders.api.dto.RechargerCreditRequest;
import com.billetplus.orders.application.usecase.ConsulterCreditUseCase;
import com.billetplus.orders.application.usecase.OuvrirRechargeUseCase;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Crédit prépayé de l'organisateur (ADR 0010). L'organisateur vient toujours du jeton :
 * chacun ne voit et ne recharge que son propre crédit (jeton exigé aussi par SecurityConfig).
 */
@RestController
@RequestMapping("/api/credit")
public class CreditController {

    private final ConsulterCreditUseCase consulterCredit;
    private final OuvrirRechargeUseCase ouvrirRecharge;

    public CreditController(ConsulterCreditUseCase consulterCredit, OuvrirRechargeUseCase ouvrirRecharge) {
        this.consulterCredit = consulterCredit;
        this.ouvrirRecharge = ouvrirRecharge;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<CreditDto> consulter(Authentication auth) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(CreditDto.from(consulterCredit.execute((UUID) auth.getPrincipal())));
    }

    /** Ouvre le paiement d'une recharge ; le solde n'est crédité qu'à la réception du webhook signé. */
    @PostMapping("/recharges")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<Map<String, Object>> recharger(@Valid @RequestBody RechargerCreditRequest request,
                                                         Authentication auth) {
        OuvrirRechargeUseCase.LienRecharge lien = ouvrirRecharge.execute((UUID) auth.getPrincipal(),
            request.montantFcfa(), request.moyen(), request.telephone());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(Map.of("rechargeId", lien.rechargeId(), "moyen", lien.moyen().name(), "url", lien.url()));
    }
}
