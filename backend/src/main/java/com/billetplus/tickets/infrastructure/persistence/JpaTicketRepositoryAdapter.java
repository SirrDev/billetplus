package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.tickets.application.port.TicketRepository;
import com.billetplus.tickets.domain.Ticket;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur du port TicketRepository. Même stratégie de sauvegarde que
 * {@link JpaTicketCategoryRepositoryAdapter} : recharger l'entité gérée et y
 * recopier l'état du domaine, pour conserver la colonne version.
 */
@Repository
class JpaTicketRepositoryAdapter implements TicketRepository {

    private final SpringDataTicketRepository springRepo;

    JpaTicketRepositoryAdapter(SpringDataTicketRepository springRepo) {
        this.springRepo = springRepo;
    }

    /**
     * Lecture seule, sans exigence de transaction. Dans la transaction d'un cas
     * d'usage, l'entité chargée reste dans la session : le save() qui suit
     * contrôle la version lue ici, pas la plus récente en base.
     */
    @Override
    public Optional<Ticket> findById(UUID id) {
        return springRepo.findById(id).map(TicketMapper::versDomaine);
    }

    /**
     * MANDATORY, pour la même raison que {@link JpaTicketCategoryRepositoryAdapter#save} :
     * hors transaction de l'appelant, deux scans concurrents du même billet
     * seraient tous deux acceptés sans erreur.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException hors transaction
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Ticket save(Ticket ticket) {
        TicketJpaEntity entity = springRepo.findById(ticket.getId())
            .map(existante -> {
                TicketMapper.copierDans(ticket, existante);
                return existante;
            })
            .orElseGet(() -> TicketMapper.versNouvelleEntite(ticket));
        return TicketMapper.versDomaine(springRepo.save(entity));
    }
}
