package com.billetplus.events.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.domain.AffectationAgent;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.EventStatus;
import com.billetplus.shared.exception.BusinessConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Affectation des agents au contrôle d'entrée d'un événement (niveau 2).
 *
 * Seul l'organisateur de l'événement affecte, et seulement SES agents (créés
 * par lui, lu par le port public du module auth, ADR 0005). Même contrôle de
 * propriété qu'EventService.publier / annuler (EventService.getPourSonOrganisateur) :
 * événement inconnu ou brouillon d'un autre → 404, événement visible d'un autre → 403.
 */
@Service
public class AffectationAgentService {

    private final EventService eventService;
    private final AffectationAgentRepository affectationRepository;
    private final Comptes comptes;

    public AffectationAgentService(EventService eventService, AffectationAgentRepository affectationRepository,
                                   Comptes comptes) {
        this.eventService = eventService;
        this.affectationRepository = affectationRepository;
        this.comptes = comptes;
    }

    /**
     * Idempotent : affecter un agent déjà affecté ne change rien.
     *
     * @throws NoSuchElementException    si l'événement n'existe pas, ou si l'agent n'est pas un
     *                                   agent de cet organisateur (même réponse pour un agent inconnu)
     * @throws AccessDeniedException     si l'événement, visible, appartient à un autre organisateur
     * @throws BusinessConflictException si l'événement est annulé ou terminé
     */
    @Transactional
    public void affecter(UUID eventId, UUID agentId, UUID organisateurId) {
        Event event = evenementDe(eventId, organisateurId);
        if (event.getStatus() == EventStatus.ANNULE || event.getStatus() == EventStatus.TERMINE) {
            throw new BusinessConflictException("Impossible d'affecter un agent à un événement annulé ou terminé");
        }
        if (!comptes.estAgentCreePar(agentId, organisateurId)) {
            throw new NoSuchElementException("Agent introuvable : " + agentId);
        }
        if (!affectationRepository.existe(eventId, agentId)) {
            affectationRepository.save(new AffectationAgent(eventId, agentId));
        }
    }

    /**
     * Idempotent : retirer un agent non affecté ne change rien.
     *
     * @throws NoSuchElementException si l'événement n'existe pas
     * @throws AccessDeniedException  si le demandeur n'est pas l'organisateur de l'événement
     */
    @Transactional
    public void retirer(UUID eventId, UUID agentId, UUID organisateurId) {
        evenementDe(eventId, organisateurId);
        affectationRepository.supprimer(eventId, agentId);
    }

    /**
     * Agents affectés à un de SES événements, par identifiant : le nom vient de GET /api/agents.
     *
     * @throws NoSuchElementException si l'événement n'existe pas
     * @throws AccessDeniedException  si le demandeur n'est pas l'organisateur de l'événement
     */
    @Transactional(readOnly = true)
    public List<UUID> listerAgents(UUID eventId, UUID organisateurId) {
        evenementDe(eventId, organisateurId);
        return affectationRepository.listerAgents(eventId);
    }

    /** Agent retiré par son organisateur (AgentRetire) : le module auth a contrôlé la propriété. */
    @Transactional
    public void retirerDeTousLesEvenements(UUID agentId) {
        affectationRepository.supprimerToutes(agentId);
    }

    @Transactional(readOnly = true)
    public boolean estAffecte(UUID eventId, UUID agentId) {
        return eventId != null && agentId != null && affectationRepository.existe(eventId, agentId);
    }

    private Event evenementDe(UUID eventId, UUID organisateurId) {
        return eventService.getPourSonOrganisateur(eventId, organisateurId);
    }
}
