package com.billetplus.orders.api;

import com.billetplus.orders.api.dto.CommandeARembourserDto;
import com.billetplus.orders.application.usecase.ListerCommandesARembourserUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Remboursements manuels (décision B) : réservé à l'administrateur. Données personnelles : no-store. */
@RestController
@RequestMapping("/api/admin/commandes-a-rembourser")
public class CommandesARembourserController {

    private final ListerCommandesARembourserUseCase lister;

    public CommandesARembourserController(ListerCommandesARembourserUseCase lister) {
        this.lister = lister;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CommandeARembourserDto>> lister() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(lister.execute().stream().map(CommandeARembourserDto::from).toList());
    }
}
