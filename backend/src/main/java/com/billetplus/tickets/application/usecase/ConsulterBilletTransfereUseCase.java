package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.JetonTransfert;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0008) : le destinataire d'un transfert consulte son
 * billet. Il présente le jeton reçu par lien ET son mobile : un lien seul, transmis à
 * la mauvaise personne ou intercepté, ne suffit pas.
 *
 * Billet inconnu, non transféré, jeton faux ou mobile différent : la même
 * NoSuchElementException (404), pour que la réponse ne dise pas lequel des deux
 * facteurs est faux.
 */
@Service
public class ConsulterBilletTransfereUseCase {

    /** Billet du destinataire. {@code qr} est vide s'il n'est plus valable (scanné, annulé...). */
    public record BilletTransfere(UUID id, UUID eventId, String categorie, String statut, Optional<String> qr) {
    }

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final QRSigner qrSigner;

    public ConsulterBilletTransfereUseCase(TicketRepository ticketRepository,
                                           TicketCategoryRepository categoryRepository, QRSigner qrSigner) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.qrSigner = qrSigner;
    }

    @Transactional(readOnly = true)
    public BilletTransfere execute(UUID ticketId, String jeton, String telephoneE164) {
        Optional<Ticket> trouve = ticketRepository.findById(ticketId);
        if (trouve.isEmpty()) {
            // Même travail qu'une vérification réelle (hachage du jeton) : un billet inconnu
            // ne doit pas répondre plus vite qu'un jeton faux.
            JetonTransfert.empreinteDe(jeton == null ? "" : jeton);
        }
        Ticket ticket = trouve
            .filter(t -> t.appartientAuDestinataire(telephoneE164, jeton))
            .orElseThrow(() -> new NoSuchElementException("Billet introuvable"));
        Optional<String> qr = ticket.getStatus() == TicketStatus.EMIS
            ? Optional.of(qrSigner.sign(ticket.getId(), ticket.getEventId(), ticket.getQrSigneLe()))
            : Optional.empty();
        String categorie = categoryRepository.findById(ticket.getCategoryId()).map(TicketCategory::getNom).orElse("");
        return new BilletTransfere(ticket.getId(), ticket.getEventId(), categorie, ticket.getStatus().name(), qr);
    }
}
