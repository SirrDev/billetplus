package com.billetplus.tickets.application.port;

import com.billetplus.tickets.domain.TicketCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (niveau 3, voir ADR 0002) — persistance des catégories de billets.
 * L'implémentation JPA (entité séparée + mapper + adaptateur) vit dans
 * infrastructure/persistence/ (voir feature/backend-tickets-jpa-mapper).
 *
 * Verrouillage optimiste : l'adaptateur s'appuie sur une colonne version
 * absente du domaine. La protection contre la survente n'est effective que si
 * {@link #findById(UUID)}, la modification du domaine et {@link #save(TicketCategory)}
 * s'exécutent dans la MÊME transaction : c'est à l'appelant (cas d'usage
 * annoté @Transactional) de la garantir. L'adaptateur JPA refuse un save()
 * appelé hors transaction (propagation MANDATORY).
 */
public interface TicketCategoryRepository {

    Optional<TicketCategory> findById(UUID id);

    /** Catégories d'un événement, de la moins chère à la plus chère. */
    List<TicketCategory> findByEventId(UUID eventId);

    TicketCategory save(TicketCategory category);

    /**
     * Ajoute {@code quantite} aux places vendues, en une requête atomique, si le
     * quota le permet (levier prévu par l'ADR 0003, activé par la mesure n° 16).
     *
     * Sans conflit de version : les émissions concurrentes s'attendent sur le verrou
     * de la ligne, et PostgreSQL réévalue la condition sur la valeur à jour, donc
     * aucune survente ni mise à jour perdue. La version est incrémentée, pour que
     * tout save() concurrent fondé sur une lecture plus ancienne échoue.
     *
     * Exige une transaction ouverte : le verrou est tenu jusqu'à sa fin.
     *
     * @return faux si le quota ne le permet pas (rien n'est modifié)
     */
    boolean consommerQuota(UUID categoryId, int quantite);
}
