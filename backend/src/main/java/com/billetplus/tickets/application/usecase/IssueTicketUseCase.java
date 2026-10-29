package com.billetplus.tickets.application.usecase;

import com.billetplus.tickets.application.port.QRSigner;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — émet {@code quantite} billets d'une
 * catégorie pour une commande, en consommant le quota correspondant, puis
 * signe le QR code de chaque billet (ADR 0004).
 *
 * L'invariant anti-overselling vit dans {@link TicketCategory#reserver(int)} ;
 * cette classe ne fait qu'orchestrer.
 *
 * {@code @Transactional} est indispensable : la lecture de la catégorie, la
 * réservation et les sauvegardes forment une seule unité atomique (ADR 0003).
 * C'est aussi ce qui rend effectif le verrouillage optimiste de la catégorie
 * (voir {@link TicketCategoryRepository}) : deux émissions concurrentes sur la
 * même catégorie ne peuvent pas toutes deux valider leur réservation.
 */
@Service
public class IssueTicketUseCase {

    /**
     * Plafond de billets par émission. Borne la durée de la transaction (une
     * insertion et une signature par billet, catégorie verrouillée pendant ce
     * temps) et la taille d'une commande. Le module orders devra appliquer la
     * même limite à la création de la commande.
     */
    public static final int QUANTITE_MAX_PAR_EMISSION = 20;

    private final TicketCategoryRepository categoryRepository;
    private final TicketRepository ticketRepository;
    private final QRSigner qrSigner;

    public IssueTicketUseCase(TicketCategoryRepository categoryRepository,
                              TicketRepository ticketRepository,
                              QRSigner qrSigner) {
        this.categoryRepository = categoryRepository;
        this.ticketRepository = ticketRepository;
        this.qrSigner = qrSigner;
    }

    /**
     * @throws IllegalArgumentException si un paramètre est absent, si la quantité n'est pas strictement
     *                                  positive ou si elle dépasse {@link #QUANTITE_MAX_PAR_EMISSION}
     * @throws NoSuchElementException   si la catégorie n'existe pas
     * @throws BilletsDejaEmisException si des billets de cette catégorie ont déjà été émis pour la commande
     * @throws QuotaDepasseException    si la quantité dépasse le quota restant
     */
    @Transactional
    public List<IssuedTicket> execute(UUID categoryId, UUID orderId, int quantite) {
        if (categoryId == null || orderId == null) {
            throw new IllegalArgumentException("categoryId et orderId sont obligatoires");
        }
        if (quantite > QUANTITE_MAX_PAR_EMISSION) {
            throw new IllegalArgumentException(
                "Au plus %d billets par émission".formatted(QUANTITE_MAX_PAR_EMISSION));
        }

        TicketCategory category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("Catégorie de billet introuvable : " + categoryId));

        if (ticketRepository.existsByOrderIdAndCategoryId(orderId, categoryId)) {
            throw new BilletsDejaEmisException(orderId, categoryId);
        }

        // Lève IllegalArgumentException (quantité <= 0) ou QuotaDepasseException,
        // sans modifier la catégorie : rien n'a encore été écrit à ce stade.
        category.reserver(quantite);
        categoryRepository.save(category);

        List<IssuedTicket> emis = new ArrayList<>(quantite);
        for (int i = 0; i < quantite; i++) {
            Ticket ticket = ticketRepository.save(new Ticket(category.getEventId(), categoryId, orderId));
            String qrContent = qrSigner.sign(ticket.getId(), ticket.getEventId(), ticket.getEmisLe());
            emis.add(new IssuedTicket(ticket, qrContent));
        }
        return emis;
    }
}
