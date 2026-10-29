package com.billetplus.tickets.api;

import com.billetplus.tickets.api.dto.CreateTicketCategoryRequest;
import com.billetplus.tickets.api.dto.TicketCategoryDto;
import com.billetplus.tickets.application.usecase.CreateTicketCategoryUseCase;
import com.billetplus.tickets.application.usecase.ListTicketCategoriesUseCase;
import com.billetplus.tickets.application.usecase.ListTicketCategoriesUseCase.ResultatCategories;
import com.billetplus.tickets.domain.TicketCategory;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Catégories de billets d'un événement. L'URL est rangée sous /api/events,
 * mais le contrôleur appartient au module tickets : il ne parle au module
 * events que par son port public (ADR 0005), via les cas d'usage.
 *
 * GET est public (règle « GET /api/events/** » de SecurityConfig) mais
 * sensible à l'identité, comme GET /api/events/{id}.
 */
@RestController
@RequestMapping("/api/events/{eventId}/categories")
public class TicketCategoryController {

    private final CreateTicketCategoryUseCase createCategory;
    private final ListTicketCategoriesUseCase listCategories;

    public TicketCategoryController(CreateTicketCategoryUseCase createCategory,
                                    ListTicketCategoriesUseCase listCategories) {
        this.createCategory = createCategory;
        this.listCategories = listCategories;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<TicketCategoryDto> create(@PathVariable UUID eventId,
                                                    @Valid @RequestBody CreateTicketCategoryRequest request,
                                                    Authentication auth) {
        UUID organisateurId = (UUID) auth.getPrincipal();
        TicketCategory category = createCategory.execute(
            eventId, organisateurId, request.nom(), request.prixFcfa(), request.quotaTotal());
        return ResponseEntity
            .created(URI.create("/api/events/" + eventId + "/categories/" + category.getId()))
            .body(TicketCategoryDto.pourLOrganisateur(category));
    }

    /** {@code viewerId} vaut null pour un visiteur anonyme (voir EventController.get). */
    @GetMapping
    public ResponseEntity<List<TicketCategoryDto>> list(@PathVariable UUID eventId,
                                                        @AuthenticationPrincipal UUID viewerId) {
        ResultatCategories resultat = listCategories.execute(eventId, viewerId);
        List<TicketCategoryDto> dtos = resultat.categories().stream()
            .map(resultat.vuParLOrganisateur() ? TicketCategoryDto::pourLOrganisateur : TicketCategoryDto::pourUnAcheteur)
            .toList();
        return ResponseEntity.ok(dtos);
    }
}
