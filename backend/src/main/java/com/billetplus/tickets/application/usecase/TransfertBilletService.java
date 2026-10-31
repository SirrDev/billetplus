package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.tickets.api.publique.TransfertBillets;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.JetonTransfert;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.exception.VenteFermeeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002 et ADR 0008) : transfert d'un billet vers un
 * autre mobile. Implémente le port public {@link TransfertBillets}.
 *
 * {@code @Transactional} est indispensable : la lecture du billet et son
 * enregistrement partagent la transaction, pour que le @Version des billets
 * rejette un transfert concurrent d'un scan (409) au lieu d'écraser son statut.
 */
@Service
class TransfertBilletService implements TransfertBillets {

    private final TicketRepository ticketRepository;
    private final StatutEvenements statutEvenements;

    TransfertBilletService(TicketRepository ticketRepository, StatutEvenements statutEvenements) {
        this.ticketRepository = ticketRepository;
        this.statutEvenements = statutEvenements;
    }

    @Override
    @Transactional
    public String transferer(UUID orderId, UUID ticketId, String telephoneE164) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .filter(t -> t.getOrderId().equals(orderId))
            // Billet d'une autre commande : même réponse qu'un identifiant inconnu.
            .orElseThrow(() -> new NoSuchElementException("Billet introuvable"));
        if (!statutEvenements.estOuvertALaVente(ticket.getEventId())) {
            throw new VenteFermeeException();
        }
        JetonTransfert jeton = JetonTransfert.generer();
        ticket.transfererA(telephoneE164, jeton.empreinte());
        ticketRepository.save(ticket);
        return jeton.enClair();
    }
}
