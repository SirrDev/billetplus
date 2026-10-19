package com.billetplus.events.infrastructure;

import com.billetplus.events.domain.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
