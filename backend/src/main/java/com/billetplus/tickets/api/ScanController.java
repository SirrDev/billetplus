package com.billetplus.tickets.api;

import com.billetplus.tickets.api.dto.ScanRequest;
import com.billetplus.tickets.api.dto.ScanResponse;
import com.billetplus.tickets.application.usecase.ScanTicketUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Contrôle d'entrée : un agent (ou l'organisateur) scanne un billet à l'entrée
 * d'un événement. Toutes les issues d'un scan (accepté, déjà scanné, non
 * valable, autre événement, invalide) sont des réponses 200 : ce sont des
 * situations normales à l'entrée, pas des erreurs.
 *
 * 403 si l'utilisateur ne contrôle pas cet événement, 401 sans jeton,
 * 400 si le QR dépasse 1024 caractères.
 */
@RestController
@RequestMapping("/api/events/{eventId}/scans")
public class ScanController {

    private final ScanTicketUseCase scanTicket;

    public ScanController(ScanTicketUseCase scanTicket) {
        this.scanTicket = scanTicket;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT', 'ORGANISATEUR')")
    public ResponseEntity<ScanResponse> scan(@PathVariable UUID eventId, @Valid @RequestBody ScanRequest request,
                                             Authentication auth) {
        UUID utilisateurId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ScanResponse.from(scanTicket.execute(request.qrContent(), eventId, utilisateurId)));
    }
}
