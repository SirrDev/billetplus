package com.billetplus.events.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.domain.Event;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Événements qu'un agent peut contrôler : ceux auxquels un organisateur l'a affecté
 * (event_agents), publiés, à venir ou en cours. Lecture pure, niveau 1 (ADR 0002),
 * interne au module events qui possède les affectations : aucune frontière franchie.
 *
 * L'agent vient du jeton, jamais de la requête. La liste ne donne aucun droit : la route
 * de scan vérifie de nouveau l'affectation (403), et c'est elle qui fait foi.
 */
@Service
public class EvenementsAControlerService {

    /**
     * Un événement sans heure de fin reste listé pendant cette durée après son début :
     * l'entrée se contrôle encore une fois les portes ouvertes.
     */
    public static final Duration DUREE_SANS_FIN_CONNUE = Duration.ofHours(24);

    private final EventRepository eventRepository;
    private final Comptes comptes;
    private final Clock clock;

    public EvenementsAControlerService(EventRepository eventRepository, Comptes comptes, Clock clock) {
        this.eventRepository = eventRepository;
        this.comptes = comptes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Event> pourLAgent(UUID agentId) {
        if (agentId == null) {
            throw new IllegalArgumentException("agentId est obligatoire");
        }
        // Agent retiré : aucune liste, même si une affectation est restée en base (même règle que le scan).
        if (!comptes.estAgentActif(agentId)) {
            return List.of();
        }
        Instant maintenant = Instant.now(clock);
        return eventRepository.findAffectesA(agentId, maintenant.minus(DUREE_SANS_FIN_CONNUE), maintenant);
    }
}
