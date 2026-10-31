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
 * recalculé avec les mêmes données qu'à l'émission (identifiant, événement, date du
 * QR en vigueur) : le contrôle d'entrée l'accepte comme l'original.
 */
@Service
class BilletsDeCommandeService implements BilletsDeCommande {

    private record Billet(UUID id, UUID categoryId, String categoryNom, String statut, Optional<String> qr,
                          Optional<String> transfereA)
        implements BilletVendu {
    }

    /** Statut exposé à l'acheteur pour un billet transféré (ADR 0008). */
    static final String TRANSFERE = "TRANSFERE";

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
                ticket.estTransfere() ? TRANSFERE : ticket.getStatus().name(),
                // Un billet transféré n'a plus de QR pour l'acheteur : il appartient au destinataire.
                ticket.getStatus() == TicketStatus.EMIS && !ticket.estTransfere()
                    ? Optional.of(qrSigner.sign(ticket.getId(), ticket.getEventId(), ticket.getQrSigneLe()))
                    : Optional.empty(),
                Optional.ofNullable(ticket.getTransfereA()).map(BilletsDeCommandeService::masquer)))
            .toList();
    }

    /** +221771234567 -> « 77 ••• •• 67 ». */
    static String masquer(String telephoneE164) {
        String national = telephoneE164.substring(4);
        return national.substring(0, 2) + " ••• •• " + national.substring(7);
    }

    private String nomDe(UUID categoryId) {
        return categoryRepository.findById(categoryId).map(TicketCategory::getNom).orElse("");
    }
}
