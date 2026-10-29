package com.billetplus.events.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Liste publique et recherche d'événements : lecture pure, niveau 1 (ADR 0002).
 *
 * Seuls les événements publiés, à venir ou en cours, y figurent : jamais un
 * brouillon (même règle que getVisible : un brouillon n'existe pas pour le public),
 * ni un événement annulé ou terminé.
 *
 * La pagination est obligatoire et bornée : aucune requête ne peut lire toute la table.
 */
@Service
public class RechercheEvenementsService {

    public static final int TAILLE_PAR_DEFAUT = 20;
    public static final int TAILLE_MAX = 50;
    /** Au-delà, la recherche n'a plus de sens et coûte cher (trigrammes). */
    public static final int RECHERCHE_LONGUEUR_MAX = 100;

    private final EventRepository eventRepository;
    private final Clock clock;

    public RechercheEvenementsService(EventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /**
     * @param recherche texte cherché dans le titre (casse ignorée) ; null ou vide : tous
     * @param page      numéro de page, à partir de 0
     * @param taille    entre 1 et {@link #TAILLE_MAX}
     * @throws IllegalArgumentException (400) si un paramètre sort de ses bornes
     */
    @Transactional(readOnly = true)
    public PageEvenements rechercher(String recherche, int page, int taille) {
        if (page < 0) {
            throw new IllegalArgumentException("page doit être positive ou nulle");
        }
        if (taille < 1 || taille > TAILLE_MAX) {
            throw new IllegalArgumentException("taille doit être comprise entre 1 et " + TAILLE_MAX);
        }
        if (recherche != null && recherche.length() > RECHERCHE_LONGUEUR_MAX) {
            throw new IllegalArgumentException("q ne doit pas dépasser " + RECHERCHE_LONGUEUR_MAX + " caractères");
        }
        return eventRepository.rechercherPublies(recherche, Instant.now(clock), page, taille);
    }
}
