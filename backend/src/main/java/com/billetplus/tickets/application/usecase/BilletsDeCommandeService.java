package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.api.publique.BilletVendu;
import com.billetplus.tickets.api.publique.BilletsDeCommande;
import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.TicketStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port public {@link BilletsDeCommande} (ADR 0005). Le QR est
 * recalculé avec les mêmes données qu'à l'émission (identifiant, événement,
 * emisLe) : le contrôle d'entrée l'accepte comme l'original.
 */
@Service
class BilletsDeCommandeService implements BilletsDeCommande {

    private record Billet(UUID id, UUID categoryId, String categoryNom, String statut, Optional<String> qr)
        implements BilletVendu {
    }

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final QRSigner qrSigner;

    BilletsDeCommandeService(TicketRepository ticketRepository, TicketCategoryRepository categoryRepository,
                             QRSigner qrSigner) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.qrSigner = qrSigner;
    }

    @Override
    public List<BilletVendu> billetsDe(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        Map<UUID, String> nomsDesCategories = new HashMap<>();
        return ticketRepository.findByOrderId(orderId).stream()
            .sorted(Comparator.comparing(Ticket::getCategoryId).thenComparing(Ticket::getId))
            .map(ticket -> (BilletVendu) new Billet(
                ticket.getId(),
                ticket.getCategoryId(),
                nomsDesCategories.computeIfAbsent(ticket.getCategoryId(), this::nomDe),
                ticket.getStatus().name(),
                ticket.getStatus() == TicketStatus.EMIS
                    ? Optional.of(qrSigner.sign(ticket.getId(), ticket.getEventId(), ticket.getEmisLe()))
                    : Optional.empty()))
            .toList();
    }

    private String nomDe(UUID categoryId) {
        return categoryRepository.findById(categoryId).map(TicketCategory::getNom).orElse("");
    }
}
