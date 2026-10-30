package com.billetplus.events.infrastructure;

import com.billetplus.events.domain.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/ uniquement.
 *
 * Event.venue est en FetchType.LAZY et spring.jpa.open-in-view vaut false :
 * la session Hibernate est fermée dès la sortie d'EventService. Sans chargement
 * explicite, EventDto.from() (appelé dans le controller, hors transaction) lirait
 * le nom du lieu sur un proxy non initialisé et lèverait une
 * LazyInitializationException.
 *
 * Le @EntityGraph charge l'événement ET son lieu en une seule requête (jointure),
 * sans rendre la relation EAGER pour tous les autres usages.
 */
interface SpringDataEventRepository extends JpaRepository<Event, UUID> {

    @Override
    @EntityGraph(attributePaths = "venue")
    Optional<Event> findById(UUID id);

    @EntityGraph(attributePaths = "venue")
    List<Event> findByOrganizerId(UUID organizerId);

    /**
     * Liste publique (niveau 1, ADR 0002). « ilike » (Hibernate 6) produit un
     * ILIKE PostgreSQL, que l'index idx_events_titre_trgm (pg_trgm, V3) peut servir ;
     * un lower(...) like ... ne le pourrait pas. Le motif arrive déjà échappé
     * (\ devant %, _ et \), d'où « escape '\' ».
     *
     * Le @EntityGraph charge le lieu dans la même requête : le DTO public lit son
     * nom hors transaction (open-in-view: false, voir findById).
     */
    @EntityGraph(attributePaths = "venue")
    @Query(value = """
        select e from Event e
        where e.status = com.billetplus.events.domain.EventStatus.PUBLIE
          and (e.debuteLe > :maintenant or (e.termineLe is not null and e.termineLe > :maintenant))
          and e.titre ilike :motif escape '\\'
        order by e.debuteLe asc, e.id asc
        """,
        countQuery = """
        select count(e) from Event e
        where e.status = com.billetplus.events.domain.EventStatus.PUBLIE
          and (e.debuteLe > :maintenant or (e.termineLe is not null and e.termineLe > :maintenant))
          and e.titre ilike :motif escape '\\'
        """)
    Page<Event> rechercherPublies(@Param("motif") String motif, @Param("maintenant") Instant maintenant, Pageable pageable);

    /**
     * Événements à contrôler par un agent : affectation (event_agents, même module) et
     * statut PUBLIE ; début récent ou à venir, ou fin encore à venir. Le lieu est chargé
     * dans la même requête (DTO lu hors transaction, voir findById).
     */
    @EntityGraph(attributePaths = "venue")
    @Query("""
        select e from Event e
        where e.status = com.billetplus.events.domain.EventStatus.PUBLIE
          and e.id in (select a.eventId from AffectationAgent a where a.agentId = :agentId)
          and (e.debuteLe > :debutApres or (e.termineLe is not null and e.termineLe > :maintenant))
        order by e.debuteLe asc, e.id asc
        """)
    List<Event> findAffectesA(@Param("agentId") UUID agentId, @Param("debutApres") Instant debutApres,
                              @Param("maintenant") Instant maintenant);
}
