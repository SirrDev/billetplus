package com.billetplus.events.application;

import com.billetplus.events.domain.Event;

import java.util.List;

/**
 * Une page de résultats, sans dépendance à Spring Data : le port EventRepository
 * reste indépendant de l'infrastructure (ADR 0002).
 *
 * @param page   numéro de la page, à partir de 0
 * @param taille nombre maximal d'éléments par page
 * @param total  nombre total d'éléments, toutes pages confondues
 */
public record PageEvenements(List<Event> contenu, int page, int taille, long total) {
}
