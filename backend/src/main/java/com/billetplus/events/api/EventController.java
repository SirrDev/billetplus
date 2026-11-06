package com.billetplus.events.api;

import com.billetplus.events.api.dto.CreateEventRequest;
import com.billetplus.events.api.dto.DefinirPositionRequest;
import com.billetplus.events.api.dto.EvenementPublicDto;
import com.billetplus.events.api.dto.EventDto;
import com.billetplus.events.api.dto.PageEvenementsPublicsDto;
import com.billetplus.events.api.dto.UpdateEventRequest;
import com.billetplus.events.application.AffectationAgentService;
import com.billetplus.events.application.EvenementsAControlerService;
import com.billetplus.events.application.EventService;
import com.billetplus.events.application.RechercheEvenementsService;
import com.billetplus.events.domain.CategorieEvenement;
import com.billetplus.events.domain.Event;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final AffectationAgentService affectationAgentService;
    private final RechercheEvenementsService rechercheEvenementsService;
    private final EvenementsAControlerService evenementsAControlerService;

    public EventController(EventService eventService, AffectationAgentService affectationAgentService,
                           RechercheEvenementsService rechercheEvenementsService,
                           EvenementsAControlerService evenementsAControlerService) {
        this.eventService = eventService;
        this.affectationAgentService = affectationAgentService;
        this.rechercheEvenementsService = rechercheEvenementsService;
        this.evenementsAControlerService = evenementsAControlerService;
    }

    /**
     * Événements que l'agent du jeton peut contrôler (affecté, publiés, à venir ou en cours).
     * Vue publique de l'événement (sans organizerId). Doit précéder la règle publique
     * GET /api/events/** dans SecurityConfig, comme /mine.
     */
    @GetMapping("/affectes")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<List<EvenementPublicDto>> affectes(Authentication auth) {
        UUID agentId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(evenementsAControlerService.pourLAgent(agentId).stream().map(EvenementPublicDto::from).toList());
    }

    /**
     * Liste publique (voir SecurityConfig : GET /api/events/** est permitAll) :
     * événements publiés, à venir ou en cours, du plus proche au plus lointain.
     * {@code q} filtre sur le titre, {@code categorie} sur la catégorie (MUSIQUE, SPORT...,
     * casse ignorée ; inconnue : 400). Pagination bornée (taille 1 à 50, sinon 400).
     */
    @GetMapping
    public ResponseEntity<PageEvenementsPublicsDto> lister(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categorie,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + RechercheEvenementsService.TAILLE_PAR_DEFAUT) int taille) {
        CategorieEvenement filtre = categorie == null || categorie.isBlank() ? null : CategorieEvenement.lire(categorie);
        return ResponseEntity.ok(PageEvenementsPublicsDto.from(rechercheEvenementsService.rechercher(q, filtre, page, taille)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> create(@Valid @RequestBody CreateEventRequest request, Authentication auth) {
        UUID organizerId = (UUID) auth.getPrincipal();
        Event event = eventService.create(
            request.titre(), request.description(), request.debuteLe(),
            request.termineLe(), request.venueId(), organizerId, request.categorie()
        );
        return ResponseEntity.created(URI.create("/api/events/" + event.getId())).body(EventDto.from(event));
    }

    /**
     * Route publique (voir SecurityConfig) mais sensible à l'identité : un brouillon
     * n'est visible que par son organisateur, tous les autres reçoivent un 404.
     *
     * {@code viewerId} vaut null pour un visiteur non connecté : son principal est la
     * chaîne "anonymousUser", non assignable à UUID, donc le résolveur
     * d'@AuthenticationPrincipal renseigne null au lieu de lever une erreur.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventDto> get(@PathVariable UUID id, @AuthenticationPrincipal UUID viewerId) {
        return ResponseEntity.ok(EventDto.pourLeVisiteur(eventService.getVisible(id, viewerId), viewerId));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<List<EventDto>> mine(Authentication auth) {
        UUID organizerId = (UUID) auth.getPrincipal();
        List<EventDto> events = eventService.listMine(organizerId).stream().map(EventDto::from).toList();
        return ResponseEntity.ok(events);
    }

    /**
     * Modifie un de SES événements : brouillon entièrement, publié pour le titre et la
     * description seulement (dates changées → 409), annulé ou terminé → 409.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> modifier(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request,
                                             Authentication auth) {
        UUID requestingUserId = (UUID) auth.getPrincipal();
        Event event = eventService.modifier(id, request.titre(), request.description(),
            request.debuteLe(), request.termineLe(), request.categorie(), requestingUserId);
        return ResponseEntity.ok(EventDto.from(event));
    }

    /** Définit la position GPS d'un de SES événements, tant qu'il est un brouillon. Publié, annulé ou terminé → 409. */
    @PutMapping("/{id}/position")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> definirPosition(@PathVariable UUID id, @Valid @RequestBody DefinirPositionRequest request,
                                                    Authentication auth) {
        Event event = eventService.definirPosition(id, request.latitude(), request.longitude(), (UUID) auth.getPrincipal());
        return ResponseEntity.ok(EventDto.from(event));
    }

    @PostMapping("/{id}/publier")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> publier(@PathVariable UUID id, Authentication auth) {
        UUID requestingUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(EventDto.from(eventService.publier(id, requestingUserId)));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<EventDto> annuler(@PathVariable UUID id, Authentication auth) {
        UUID requestingUserId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(EventDto.from(eventService.annuler(id, requestingUserId)));
    }

    /** Identifiants des agents affectés à un de SES événements. Jeton exigé aussi par SecurityConfig. */
    @GetMapping("/{id}/agents")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<List<UUID>> agentsAffectes(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(affectationAgentService.listerAgents(id, (UUID) auth.getPrincipal()));
    }

    /**
     * Affecte un de SES agents au contrôle d'entrée de l'événement. Idempotent
     * (PUT) : réaffecter ne change rien. Agent d'un autre organisateur ou inconnu → 404.
     */
    @PutMapping("/{id}/agents/{agentId}")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<Void> affecterAgent(@PathVariable UUID id, @PathVariable UUID agentId, Authentication auth) {
        affectationAgentService.affecter(id, agentId, (UUID) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    /** Retire l'agent du contrôle d'entrée. Idempotent : retirer un agent non affecté ne change rien. */
    @DeleteMapping("/{id}/agents/{agentId}")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<Void> retirerAgent(@PathVariable UUID id, @PathVariable UUID agentId, Authentication auth) {
        affectationAgentService.retirer(id, agentId, (UUID) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }
}
