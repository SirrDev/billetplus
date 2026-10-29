package com.billetplus.events.application;

import com.billetplus.events.domain.Event;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

    Optional<Event> findById(UUID id);

    Event save(Event event);

    java.util.List<Event> findByOrganizerId(UUID organizerId);

    /**
     * Événements publiés, à venir ou en cours à {@code maintenant} (début ou fin dans le
     * futur), du plus proche au plus lointain, dont le titre contient {@code recherche}
     * (sans tenir compte de la casse ; null ou vide : tous).
     */
    PageEvenements rechercherPublies(String recherche, java.time.Instant maintenant, int page, int taille);
}
