package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.api.publique.ControleEntrees;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Implémentation du port public {@link ControleEntrees} (ADR 0005), par la requête agrégée du tableau de bord. */
@Service
class ControleEntreesService implements ControleEntrees {

    private final TicketRepository ticketRepository;

    ControleEntreesService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public long billetsScannes(UUID eventId) {
        if (eventId == null) {
            return 0;
        }
        return ticketRepository.compterParCategorieEtStatut(eventId).stream()
            .filter(c -> c.statut() == TicketStatus.SCANNE)
            .mapToLong(TicketRepository.CompteBillets::nombre)
            .sum();
    }
}
