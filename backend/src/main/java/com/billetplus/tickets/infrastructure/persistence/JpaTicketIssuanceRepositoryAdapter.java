package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.application.port.TicketIssuanceRepository;
import com.billetplus.tickets.domain.exception.BilletsDejaEmisException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Adaptateur du port TicketIssuanceRepository.
 *
 * INSERT ... ON CONFLICT DO NOTHING natif, puis lecture du nombre de lignes
 * insérées (0 = émission déjà enregistrée). Sous concurrence, PostgreSQL fait
 * attendre l'INSERT tant qu'une autre transaction insère la même clé : si elle
 * est validée, rien n'est inséré ; si elle est annulée, l'insertion passe.
 *
 * Pourquoi pas les autres voies :
 * - save() de Spring Data ferait un merge (clé composite, pas de @Version) :
 *   SELECT puis UPDATE silencieux si la ligne existe, le doublon passerait ;
 * - persist + flush échoue en 23505, ce qui interrompt la transaction
 *   PostgreSQL, et un second appel dans la même transaction lève
 *   EntityExistsException (500, pas 409).
 *
 * @Component et NON @Repository : Spring applique aux beans @Repository une
 * traduction d'exceptions qui convertit toute IllegalStateException en
 * InvalidDataAccessApiUsageException. BilletsDejaEmisException en hérite (via
 * BusinessConflictException) : le 409 deviendrait un 500. Démontré par
 * TicketPersistenceIntegrationTest, qui active cette traduction comme en production.
 */
@Component
class JpaTicketIssuanceRepositoryAdapter implements TicketIssuanceRepository {

    private static final String INSERTION_SI_ABSENTE = """
        INSERT INTO ticket_issuances (order_id, category_id, quantite, emise_le)
        VALUES (:orderId, :categoryId, :quantite, :emiseLe)
        ON CONFLICT (order_id, category_id) DO NOTHING
        """;

    private final EntityManager entityManager;

    JpaTicketIssuanceRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * MANDATORY : la ligne n'a de sens que dans la transaction qui réserve le
     * quota et crée les billets ; si celle-ci est annulée, la ligne l'est aussi.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrer(UUID orderId, UUID categoryId, int quantite) {
        int inseres = entityManager.createNativeQuery(INSERTION_SI_ABSENTE)
            .setParameter("orderId", orderId)
            .setParameter("categoryId", categoryId)
            .setParameter("quantite", quantite)
            .setParameter("emiseLe", Instant.now().truncatedTo(ChronoUnit.MICROS))
            .executeUpdate();
        if (inseres == 0) {
            throw new BilletsDejaEmisException(orderId, categoryId);
        }
    }
}
