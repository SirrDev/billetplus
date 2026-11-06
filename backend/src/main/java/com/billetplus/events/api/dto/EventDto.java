package com.billetplus.events.api.dto;

import com.billetplus.events.domain.Event;

import java.time.Instant;
import java.util.UUID;

public record EventDto(
    UUID id,
    String titre,
    String description,
    Instant debuteLe,
    Instant termineLe,
    UUID venueId,
    String venueNom,
    UUID organizerId,
    String status,
    Instant creeLe,
    String categorie
) {
    /**
     * Pour la route publique GET /api/events/{id} : l'identifiant de l'organisateur
     * (identifiant interne du module auth) n'est rendu qu'à l'organisateur lui-même ; null
     * pour tout autre visiteur. La PWA s'en sert seulement pour reconnaître son propre événement.
     *
     * @param viewerId visiteur du jeton, ou null s'il est anonyme
     */
    public static EventDto pourLeVisiteur(Event event, UUID viewerId) {
        EventDto complet = from(event);
        if (event.getOrganizerId().equals(viewerId)) {
            return complet;
        }
        return new EventDto(complet.id(), complet.titre(), complet.description(), complet.debuteLe(),
            complet.termineLe(), complet.venueId(), complet.venueNom(), null, complet.status(),
            complet.creeLe(), complet.categorie());
    }

    /** Réponses réservées à l'organisateur de l'événement (routes protégées par son rôle et la propriété). */
    public static EventDto from(Event event) {
        return new EventDto(
            event.getId(), event.getTitre(), event.getDescription(),
            event.getDebuteLe(), event.getTermineLe(),
            event.getVenue().getId(), event.getVenue().getNom(),
            event.getOrganizerId(), event.getStatus().name(), event.getCreeLe(),
            event.getCategorie().name()
        );
    }
}
