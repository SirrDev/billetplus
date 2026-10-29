package com.billetplus.events.application;

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

    @Transactional
    public Event create(String titre, String description, Instant debuteLe, Instant termineLe,
                         UUID venueId, UUID organizerId) {
        Venue venue = venueRepository.findById(venueId)
            .orElseThrow(() -> new NoSuchElementException("Lieu introuvable : " + venueId));

        Event event = new Event(titre, description, debuteLe, termineLe, venue, organizerId);
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
     */
    private void verifierProprietaire(Event event, UUID requestingUserId) {
        if (!event.getOrganizerId().equals(requestingUserId)) {
            throw new AccessDeniedException("Vous n'êtes pas l'organisateur de cet événement");
        }
    }
}
