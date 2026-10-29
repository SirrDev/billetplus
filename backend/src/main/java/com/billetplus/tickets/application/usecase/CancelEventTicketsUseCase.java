package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — un événement est annulé : tous ses
 * billets encore valides (EMIS) passent à ANNULE. Le contrôle d'entrée les
 * refuse alors (NON_VALIDE), et le futur remboursement les retrouvera par leur
 * statut.
 *
 * Déclenché par l'événement applicatif EvenementAnnule (ADR 0005), dans la
 * transaction de l'annulation : {@code @Transactional} rejoint cette
 * transaction. Si l'annulation d'un billet échoue, l'annulation de l'événement
 * est annulée avec elle.
 *
 * Les billets déjà SCANNE ne sont pas touchés : leur porteur est déjà entré
 * (événement annulé pendant son déroulement). Les billets déjà ANNULE ou
 * REMBOURSE non plus, ce qui rend le cas d'usage idempotent.
 */
@Service
public class CancelEventTicketsUseCase {

    private final TicketRepository ticketRepository;

    public CancelEventTicketsUseCase(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * @return le nombre de billets annulés
     * @throws IllegalArgumentException si {@code eventId} est absent
     */
    @Transactional
    public int execute(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId est obligatoire");
        }
        List<Ticket> valides = ticketRepository.findByEventIdAndStatus(eventId, TicketStatus.EMIS);
        for (Ticket ticket : valides) {
            ticket.annuler();
            ticketRepository.save(ticket);
        }
        return valides.size();
    }
}
