package com.billetplus.events.application;

import com.billetplus.events.domain.CategorieEvenement;
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
     * (sans tenir compte de la casse ; null ou vide : tous) et de la catégorie demandée.
     *
     * @param categorie null : toutes les catégories
     */
    PageEvenements rechercherPublies(String recherche, CategorieEvenement categorie,
                                     java.time.Instant maintenant, int page, int taille);

    /**
     * Événements PUBLIÉS auxquels l'agent est affecté et qui restent à contrôler : début
     * après {@code debutApres}, ou fin encore à venir à {@code maintenant}. Du plus proche
     * au plus lointain ; le lieu est chargé (lu hors transaction par le DTO).
     */
    java.util.List<Event> findAffectesA(UUID agentId, java.time.Instant debutApres, java.time.Instant maintenant);
}
