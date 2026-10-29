package com.billetplus.tickets.infrastructure.evenements;

import com.billetplus.shared.evenements.EvenementAnnule;
import com.billetplus.tickets.application.usecase.CancelEventTicketsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adaptateur entrant du module tickets : reçoit l'annulation d'un événement,
 * publiée par le module events, et annule les billets correspondants (ADR 0005).
 *
 * {@code @EventListener} synchrone, et non {@code @TransactionalEventListener(AFTER_COMMIT)} :
 * il s'exécute dans la transaction de l'annulation, qui réussit ou échoue en
 * entier. Une exception ici annule l'annulation de l'événement : c'est voulu,
 * pour qu'un événement annulé n'ait jamais de billets encore valides.
 */
@Component
class EvenementAnnuleListener {

    private static final Logger LOG = LoggerFactory.getLogger(EvenementAnnuleListener.class);

    private final CancelEventTicketsUseCase cancelEventTickets;

    EvenementAnnuleListener(CancelEventTicketsUseCase cancelEventTickets) {
        this.cancelEventTickets = cancelEventTickets;
    }

    @EventListener
    void surAnnulation(EvenementAnnule evenement) {
        int annules = cancelEventTickets.execute(evenement.eventId());
        LOG.info("Événement {} annulé : {} billet(s) annulé(s)", evenement.eventId(), annules);
    }
}
