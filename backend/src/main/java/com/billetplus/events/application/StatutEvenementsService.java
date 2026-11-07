package com.billetplus.events.application;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.events.domain.Event;
import com.billetplus.events.domain.EventStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port public {@link StatutEvenements} (ADR 0005). Lecture
 * directe, toujours exacte : aucune copie de l'état n'est tenue ailleurs.
 */
@Service
class StatutEvenementsService implements StatutEvenements {

    private final EventRepository eventRepository;
    private final AffectationAgentRepository affectationRepository;
    private final Comptes comptes;

    StatutEvenementsService(EventRepository eventRepository, AffectationAgentRepository affectationRepository,
                            Comptes comptes) {
        this.eventRepository = eventRepository;
        this.affectationRepository = affectationRepository;
        this.comptes = comptes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estOuvertALaVente(UUID eventId) {
        return lire(eventId)
            .map(event -> event.getStatus() == EventStatus.PUBLIE)
            .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estOrganisateur(UUID eventId, UUID userId) {
        return userId != null && lire(eventId)
            .map(event -> event.getOrganizerId().equals(userId))
            .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> organisateurDe(UUID eventId) {
        return lire(eventId).map(Event::getOrganizerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<java.time.Instant> finDe(UUID eventId) {
        return lire(eventId).map(e -> e.getTermineLe() != null ? e.getTermineLe() : e.getDebuteLe());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accepteDesCategories(UUID eventId) {
        return lire(eventId)
            .map(event -> event.getStatus() == EventStatus.BROUILLON || event.getStatus() == EventStatus.PUBLIE)
            .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estAgentAffecte(UUID eventId, UUID agentId) {
        // Compte actif vérifié EN PLUS de l'affectation : une affectation commencée avant un
        // retrait peut s'écrire après lui (aucun verrou commun aux deux modules). Sans ce
        // contrôle, l'agent retiré scannerait tant que son jeton d'accès vit (15 min).
        return eventId != null && agentId != null && affectationRepository.existe(eventId, agentId)
            && comptes.estAgentActif(agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estAnnule(UUID eventId) {
        return lire(eventId)
            .map(event -> event.getStatus() == EventStatus.ANNULE)
            .orElse(false);
    }

    private Optional<Event> lire(UUID eventId) {
        return eventId == null ? Optional.empty() : eventRepository.findById(eventId);
    }
}
