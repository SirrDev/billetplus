package com.billetplus.tickets.domain.exception;

import com.billetplus.shared.exception.BusinessConflictException;

import java.time.Instant;
import java.util.UUID;

/**
 * Levée lors d'une tentative de double scan d'un billet déjà utilisé.
 * Voir cahier des charges — détection de double entrée en temps réel.
 *
 * Conflit métier (409), pas une panne : un QR présenté deux fois est une
 * situation normale à l'entrée (copie, capture d'écran partagée). L'heure du
 * premier scan est exposée pour que l'agent puisse la montrer au client.
 *
 * Message volontairement générique : le handler 409 renvoie le message au
 * client. Si un futur appelant laissait remonter cette exception, il ne doit
 * exposer ni l'identifiant du billet ni l'heure d'entrée. Ces données restent
 * accessibles par les accesseurs, pour le ResultatScan.
 */
public class TicketAlreadyScannedException extends BusinessConflictException {

    private final UUID ticketId;
    private final Instant premierScanLe;

    public TicketAlreadyScannedException(UUID ticketId, Instant premierScanLe) {
        super("Billet déjà scanné");
        this.ticketId = ticketId;
        this.premierScanLe = premierScanLe;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public Instant getPremierScanLe() {
        return premierScanLe;
    }
}
