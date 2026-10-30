package com.billetplus.tickets.api;

import com.billetplus.tickets.application.usecase.TableauDeBordUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tableau de bord de l'organisateur. Sous /api/events/** (GET public dans
 * SecurityConfig) : c'est @PreAuthorize qui exige un organisateur, et le cas
 * d'usage qui exige L'organisateur de cet événement (404 sinon).
 */
@RestController
public class TableauDeBordController {

    private final TableauDeBordUseCase tableauDeBord;

    public TableauDeBordController(TableauDeBordUseCase tableauDeBord) {
        this.tableauDeBord = tableauDeBord;
    }

    @GetMapping("/api/events/{eventId}/tableau-de-bord")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<TableauDeBordUseCase.TableauDeBord> afficher(@PathVariable UUID eventId, Authentication auth) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(tableauDeBord.execute(eventId, (UUID) auth.getPrincipal()));
    }
}
