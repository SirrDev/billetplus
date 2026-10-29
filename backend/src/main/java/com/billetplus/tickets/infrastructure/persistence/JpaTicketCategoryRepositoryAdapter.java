package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.TicketCategory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur du port TicketCategoryRepository.
 *
 * save() ne reconstruit jamais une entité neuve pour une catégorie existante :
 * il recharge l'entité gérée et y recopie l'état du domaine, pour que
 * Hibernate conserve la version lue et la contrôle au flush
 * (UPDATE ... WHERE version = ?). Dans la transaction du cas d'usage, ce
 * findById est servi par le cache de la session : c'est la version lue au
 * début du cas d'usage qui est vérifiée, pas la plus récente en base.
 */
@Repository
class JpaTicketCategoryRepositoryAdapter implements TicketCategoryRepository {

    private final SpringDataTicketCategoryRepository springRepo;

    JpaTicketCategoryRepositoryAdapter(SpringDataTicketCategoryRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<TicketCategory> findById(UUID id) {
        return springRepo.findById(id).map(TicketCategoryMapper::versDomaine);
    }

    @Override
    public List<TicketCategory> findByEventId(UUID eventId) {
        return springRepo.findByEventIdOrderByPrixFcfaAsc(eventId).stream()
            .map(TicketCategoryMapper::versDomaine)
            .toList();
    }

    /**
     * MANDATORY : exige la transaction de l'appelant, celle dans laquelle la
     * catégorie a été lue. Ouvrir ici une transaction neuve relirait la version
     * la plus récente en base : une réservation calculée sur un état périmé
     * écraserait alors le quota sans aucune erreur (survente silencieuse).
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException hors transaction
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public TicketCategory save(TicketCategory category) {
        TicketCategoryJpaEntity entity = springRepo.findById(category.getId())
            .map(existante -> {
                TicketCategoryMapper.copierDans(category, existante);
                return existante;
            })
            .orElseGet(() -> TicketCategoryMapper.versNouvelleEntite(category));
        return TicketCategoryMapper.versDomaine(springRepo.save(entity));
    }
}
