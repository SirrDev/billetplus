package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.Reversement;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur du port ReversementRepository (ADR 0009). Le mapping entre domaine et
 * entité JPA tient en deux méthodes, gardées ici.
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
        return versDomaine(springRepo.save(versEntite(reversement)));
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
