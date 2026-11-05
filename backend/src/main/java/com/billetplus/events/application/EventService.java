package com.billetplus.events.application;

import com.billetplus.events.domain.CategorieEvenement;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.Venue;
import com.billetplus.shared.evenements.EvenementAnnule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final ApplicationEventPublisher publisher;

    public EventService(EventRepository eventRepository, VenueRepository venueRepository,
                        ApplicationEventPublisher publisher) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.publisher = publisher;
    }

    /** @param categorie null : AUTRE */
    @Transactional
    public Event create(String titre, String description, Instant debuteLe, Instant termineLe,
                         UUID venueId, UUID organizerId, CategorieEvenement categorie) {
        Venue venue = venueRepository.findById(venueId)
            .orElseThrow(() -> new NoSuchElementException("Lieu introuvable : " + venueId));

        Event event = new Event(titre, description, debuteLe, termineLe, venue, organizerId, categorie);
        return eventRepository.save(event);
    }

    /**
     * Lecture brute, sans règle de visibilité : réservée à l'usage interne du
     * service (publier/annuler contrôlent la propriété eux-mêmes). Toute lecture
     * exposée à un client doit passer par {@link #getVisible(UUID, UUID)}.
     */
    @Transactional(readOnly = true)
    public Event get(UUID id) {
        return eventRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Événement introuvable : " + id));
    }

    /**
     * Lecture exposée à un visiteur, connecté ou non.
     *
     * @param viewerId identifiant du visiteur, ou null s'il est anonyme
     * @throws NoSuchElementException si l'événement n'existe pas OU s'il est un brouillon
     *                                que le visiteur n'a pas le droit de voir
     */
    @Transactional(readOnly = true)
    public Event getVisible(UUID id, UUID viewerId) {
        Event event = get(id);
        if (!event.estVisiblePar(viewerId)) {
            // 404 et non 403 : ne pas révéler l'existence d'un brouillon.
            // Message identique à celui d'un identifiant inconnu, pour qu'aucune
            // différence de réponse ne permette de distinguer les deux cas.
            throw new NoSuchElementException("Événement introuvable : " + id);
        }
        return event;
    }

    @Transactional(readOnly = true)
    public List<Event> listMine(UUID organizerId) {
        return eventRepository.findByOrganizerId(organizerId);
    }

    @Transactional
    public Event publier(UUID eventId, UUID requestingUserId) {
        Event event = get(eventId);
        verifierProprietaire(event, requestingUserId);
        event.publier();
        return eventRepository.save(event);
    }

    /**
     * Modifie un de SES événements (règles de statut dans {@link Event#modifier}).
     * Même contrôle de propriété que publier / annuler.
     */
    /** @param categorie null : inchangée */
    @Transactional
    public Event modifier(UUID eventId, String titre, String description, Instant debuteLe,
                          Instant termineLe, CategorieEvenement categorie, UUID requestingUserId) {
        Event event = get(eventId);
        verifierProprietaire(event, requestingUserId);
        event.modifier(titre, description, debuteLe, termineLe, categorie);
        return eventRepository.save(event);
    }

    /**
     * Définit la position GPS d'un de SES événements. Le lieu n'a pas de propriétaire et
     * POST /api/events accepte n'importe quel venueId : le modifier sur place pourrait déplacer
     * l'événement d'un autre organisateur. L'événement reçoit donc une copie à la nouvelle position.
     *
     * @throws com.billetplus.shared.exception.BusinessConflictException si l'événement est annulé ou terminé (HTTP 409)
     */
    @Transactional
    public Event definirPosition(UUID eventId, double latitude, double longitude, UUID requestingUserId) {
        Event event = get(eventId);
        verifierProprietaire(event, requestingUserId);
        event.changerLieu(venueRepository.save(event.getVenue().copieA(latitude, longitude)));
        return eventRepository.save(event);
    }

    /**
     * Publie {@link EvenementAnnule} dans la même transaction (ADR 0005) : les
     * modules qui écoutent (tickets annule les billets) agissent avant la
     * validation. Si l'un d'eux échoue, l'annulation de l'événement est annulée
     * avec lui.
     */
    @Transactional
    public Event annuler(UUID eventId, UUID requestingUserId) {
        Event event = get(eventId);
        verifierProprietaire(event, requestingUserId);
        event.annuler();
        Event enregistre = eventRepository.save(event);
        publisher.publishEvent(new EvenementAnnule(enregistre.getId()));
        return enregistre;
    }

    /**
     * Un organisateur ne peut agir que sur ses propres événements — règle
     * métier, pas seulement un contrôle de rôle RBAC (@PreAuthorize vérifie
     * "est organisateur", pas "est LE organisateur de CET événement").
     *
     * Réponse alignée sur la visibilité (branche feature/backend-tests-http-routes-existantes) :
     * - événement que le demandeur ne peut pas voir (brouillon d'un autre) :
     *   NoSuchElementException (404), même message qu'un identifiant inconnu,
     *   pour que l'existence d'un brouillon ne se déduise pas d'un 403 ;
     * - événement visible (publié, annulé, terminé) d'un autre organisateur :
     *   AccessDeniedException (403), son existence étant déjà publique.
     */
    private void verifierProprietaire(Event event, UUID requestingUserId) {
        if (!event.getOrganizerId().equals(requestingUserId)) {
            if (!event.estVisiblePar(requestingUserId)) {
                throw new NoSuchElementException("Événement introuvable : " + event.getId());
            }
            throw new AccessDeniedException("Vous n'êtes pas l'organisateur de cet événement");
        }
    }

    /**
     * L'événement, si {@code requestingUserId} en est l'organisateur. Même règle
     * que publier / annuler (voir verifierProprietaire) : brouillon d'un autre → 404,
     * événement visible d'un autre → 403. Utilisé par les autres services du module.
     */
    @Transactional(readOnly = true)
    public Event getPourSonOrganisateur(UUID eventId, UUID requestingUserId) {
        Event event = get(eventId);
        verifierProprietaire(event, requestingUserId);
        return event;
    }
}
