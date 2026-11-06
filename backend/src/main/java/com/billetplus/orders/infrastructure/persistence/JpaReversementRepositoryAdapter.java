package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.Reversement;
import com.billetplus.shared.exception.BusinessConflictException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur du port ReversementRepository (ADR 0009). Le mapping entre domaine et
 * entité JPA tient en deux méthodes, gardées ici. {@code @Component} et non
 * {@code @Repository} : il lève une BusinessConflictException (409), que la traduction des
 * beans {@code @Repository} transformerait en 500 (piège du CLAUDE.md).
 */
@Component
class JpaReversementRepositoryAdapter implements ReversementRepository {

    private final SpringDataReversementRepository springRepo;

    JpaReversementRepositoryAdapter(SpringDataReversementRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Reversement save(Reversement reversement) {
        try {
            // Écrit tout de suite : un doublon (double clic, rejeu) apparaît ici, pas au commit.
            return versDomaine(springRepo.saveAndFlush(versEntite(reversement)));
        } catch (DataIntegrityViolationException e) {
            if (violeLaContrainte(e, "uq_reversements_event_reference")) {
                throw new BusinessConflictException("Ce virement est déjà enregistré pour cet événement");
            }
            throw e;
        }
    }

    @Override
    public boolean existePour(UUID eventId) {
        return springRepo.existsByEventId(eventId);
    }

    private static boolean violeLaContrainte(Throwable erreur, String contrainte) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return contrainte.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    @Override
    public long totalReverse(UUID eventId) {
        return springRepo.totalReverse(eventId);
    }

    @Override
    public Map<UUID, Long> totauxParEvenement() {
        Map<UUID, Long> totaux = new HashMap<>();
        for (Object[] ligne : springRepo.totauxParEvenement()) {
            totaux.put((UUID) ligne[0], ((Number) ligne[1]).longValue());
        }
        return totaux;
    }

    private static ReversementJpaEntity versEntite(Reversement r) {
        return new ReversementJpaEntity(r.getId(), r.getEventId(), r.getMontantFcfa(), r.getReference(), r.getMotif(),
            r.getReverseLe(), r.getEnregistrePar(), r.getEnregistreLe());
    }

    private static Reversement versDomaine(ReversementJpaEntity e) {
        return Reversement.reconstituer(e.getId(), e.getEventId(), e.getMontantFcfa(), e.getReference(), e.getMotif(),
            e.getReverseLe(), e.getEnregistrePar(), e.getEnregistreLe());
    }
}
