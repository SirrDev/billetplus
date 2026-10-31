package com.billetplus.tickets.api;

import com.billetplus.tickets.api.dto.BilletTransfereDto;
import com.billetplus.tickets.api.dto.ConsulterBilletTransfereRequest;
import com.billetplus.tickets.application.usecase.ConsulterBilletTransfereUseCase;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Billet reçu par transfert (ADR 0008), sans compte : le destinataire présente le
 * jeton reçu par lien (en-tête, jamais dans l'URL) et son mobile (corps). Route
 * publique (SecurityConfig), débit limité par IP. POST et non GET : le mobile ne
 * doit pas finir dans l'URL.
 *
 * 404 identique pour un billet inconnu, non transféré, un jeton ou un mobile faux.
 * Réponse no-store : elle contient le QR, un jeton porteur.
 */
@RestController
public class BilletTransfereController {

    public static final String EN_TETE_JETON = "X-Jeton-Billet";

    private final ConsulterBilletTransfereUseCase consulter;

    public BilletTransfereController(ConsulterBilletTransfereUseCase consulter) {
        this.consulter = consulter;
    }

    @PostMapping("/api/billets/{id}/consultation")
    public ResponseEntity<BilletTransfereDto> consulter(@PathVariable UUID id,
                                                        @RequestHeader(name = EN_TETE_JETON, required = false) String jeton,
                                                        @Valid @RequestBody ConsulterBilletTransfereRequest request) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(BilletTransfereDto.from(consulter.execute(id, jeton, request.telephone())));
    }
}
