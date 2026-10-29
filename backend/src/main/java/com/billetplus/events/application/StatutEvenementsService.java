package com.billetplus.events.application;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.events.domain.EventStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation du port public {@link StatutEvenements} (ADR 0005). Lecture
 * directe, toujours exacte : aucune copie de l'état n'est tenue ailleurs.
 */
@Service
class StatutEvenementsService implements StatutEvenements {

    private final EventRepository eventRepository;

    StatutEvenementsService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estOuvertALaVente(UUID eventId) {
        return eventId != null && eventRepository.findById(eventId)
            .map(event -> event.getStatus() == EventStatus.PUBLIE)
            .orElse(false);
    }
}
