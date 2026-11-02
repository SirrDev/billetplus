package com.billetplus.events.infrastructure;

import com.billetplus.events.application.EventRepository;
import com.billetplus.events.application.PageEvenements;
import com.billetplus.events.domain.CategorieEvenement;
import com.billetplus.events.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaEventRepositoryAdapter implements EventRepository {

    private final SpringDataEventRepository springRepo;

    JpaEventRepositoryAdapter(SpringDataEventRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return springRepo.findById(id);
    }

    @Override
    public Event save(Event event) {
        return springRepo.save(event);
    }

    @Override
    public List<Event> findByOrganizerId(UUID organizerId) {
        return springRepo.findByOrganizerId(organizerId);
    }

    @Override
    public PageEvenements rechercherPublies(String recherche, CategorieEvenement categorie, Instant maintenant,
                                            int page, int taille) {
        String motif = "%" + echapperJokers(recherche == null ? "" : recherche.trim()) + "%";
        // Liste plutôt qu'un paramètre nullable : « :c is null » sur un enum pose des problèmes de
        // typage du paramètre avec PostgreSQL ; sans filtre, la liste contient toutes les catégories.
        List<CategorieEvenement> categories = categorie == null ? List.of(CategorieEvenement.values()) : List.of(categorie);
        Page<Event> resultat = springRepo.rechercherPublies(motif, categories, maintenant, PageRequest.of(page, taille));
        return new PageEvenements(resultat.getContent(), page, taille, resultat.getTotalElements());
    }

    @Override
    public List<Event> findAffectesA(UUID agentId, Instant debutApres, Instant maintenant) {
        return springRepo.findAffectesA(agentId, debutApres, maintenant);
    }

    /**
     * Une recherche « 100% » ou « a_b » cherche ces caractères tels quels : % et _
     * sont des jokers de LIKE, échappés ici (avec la barre oblique inverse elle-même).
     */
    static String echapperJokers(String texte) {
        return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
