package com.billetplus.events.application;

import com.billetplus.events.domain.CategorieEvenement;
import com.billetplus.events.domain.Pays;
import com.billetplus.events.domain.Ville;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

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
    /** Longueur de villes.code (V18). */
    static final int CODE_VILLE_LONGUEUR_MAX = 40;

    private final EventRepository eventRepository;
    private final VilleRepository villeRepository;
    private final Clock clock;

    public RechercheEvenementsService(EventRepository eventRepository, VilleRepository villeRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.villeRepository = villeRepository;
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
        return rechercher(recherche, null, page, taille);
    }

    /**
     * @param categorie catégorie à garder ; null : toutes
     */
    @Transactional(readOnly = true)
    public PageEvenements rechercher(String recherche, CategorieEvenement categorie, int page, int taille) {
        return rechercher(recherche, categorie, null, null, page, taille);
    }

    /**
     * @param villeCode code d'une ville de la liste (ADR 0011) ; null ou vide : toutes ; inconnu : 400
     * @param paysCode  code ISO d'un pays ouvert (SN, CI...) ; null ou vide : tous ; inconnu : 400
     */
    @Transactional(readOnly = true)
    public PageEvenements rechercher(String recherche, CategorieEvenement categorie, String villeCode,
                                     String paysCode, int page, int taille) {
        if (page < 0) {
            throw new IllegalArgumentException("page doit être positive ou nulle");
        }
        if (taille < 1 || taille > TAILLE_MAX) {
            throw new IllegalArgumentException("taille doit être comprise entre 1 et " + TAILLE_MAX);
        }
        if (recherche != null && recherche.length() > RECHERCHE_LONGUEUR_MAX) {
            throw new IllegalArgumentException("q ne doit pas dépasser " + RECHERCHE_LONGUEUR_MAX + " caractères");
        }
        // Code inconnu : 400, pas une liste vide qui ferait croire qu'il n'y a rien dans cette ville.
        // Casse ignorée, comme pour la catégorie ; message sans recopier la saisie.
        if (villeCode != null && villeCode.length() > CODE_VILLE_LONGUEUR_MAX) {
            throw new IllegalArgumentException("Ville inconnue");
        }
        String ville = villeCode == null || villeCode.isBlank() ? null
            : villeRepository.findByCode(villeCode.trim().toUpperCase(Locale.ROOT))
                .map(Ville::getCode)
                .orElseThrow(() -> new IllegalArgumentException("Ville inconnue"));
        Pays pays = paysCode == null || paysCode.isBlank() ? null
            : Pays.depuisCode(paysCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Pays inconnu"));
        return eventRepository.rechercherPublies(recherche, categorie, ville, pays, Instant.now(clock), page, taille);
    }
}
