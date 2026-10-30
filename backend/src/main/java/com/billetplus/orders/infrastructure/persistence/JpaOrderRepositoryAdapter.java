package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur du port OrderRepository. Même stratégie de sauvegarde que les
 * adaptateurs du module tickets : recharger l'entité gérée et y recopier l'état
 * du domaine, pour conserver la colonne version.
 *
 * @Repository convient ici : l'adaptateur ne lève aucune BusinessConflictException
 * (la traduction d'exceptions de Spring la changerait en 500, voir CLAUDE.md).
 */
@Repository
class JpaOrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderRepository springRepo;

    JpaOrderRepositoryAdapter(SpringDataOrderRepository springRepo) {
        this.springRepo = springRepo;
    }

    /**
     * Lecture seule, sans exigence de transaction. Dans la transaction d'un cas
     * d'usage, l'entité chargée reste dans la session : le save() qui suit
     * contrôle la version lue ici, pas la plus récente en base.
     */
    @Override
    public Optional<Order> findById(UUID id) {
        return springRepo.findById(id).map(OrderMapper::versDomaine);
    }

    /**
     * MANDATORY : hors transaction de l'appelant, deux confirmations concurrentes
     * de la même commande seraient toutes deux acceptées sans erreur.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException hors transaction
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Order save(Order order) {
        OrderJpaEntity entity = springRepo.findById(order.getId())
            .map(existante -> {
                OrderMapper.copierDans(order, existante);
                return existante;
            })
            .orElseGet(() -> OrderMapper.versNouvelleEntite(order));
        return OrderMapper.versDomaine(springRepo.save(entity));
    }

    @Override
    public long compterEnAttente(NumeroTelephone telephone, UUID eventId, Instant maintenant) {
        return springRepo.countByTelephoneAndEventIdAndStatusAndExpireLeAfter(
            telephone.valeur(), eventId, OrderStatus.EN_ATTENTE_PAIEMENT, maintenant);
    }

    @Override
    public Optional<Order> findByReferencePaiement(String reference) {
        return springRepo.findByPaiementReference(reference).map(OrderMapper::versDomaine);
    }

    @Override
    public List<Order> findPayeesSansBillet(int limite) {
        return springRepo.findByStatusOrderByPayeeLeAsc(OrderStatus.PAYEE_SANS_BILLET, PageRequest.of(0, limite))
            .stream().map(OrderMapper::versDomaine).toList();
    }

    @Override
    public List<UUID> evenementsAvecCommandesPayees() {
        return springRepo.evenementsAvecStatut(OrderStatus.PAYEE);
    }

    @Override
    public List<Order> findPayeesDeLEvenement(UUID eventId, int limite) {
        return springRepo.findByEventIdAndStatusOrderByPayeeLeAsc(eventId, OrderStatus.PAYEE, PageRequest.of(0, limite))
            .stream().map(OrderMapper::versDomaine).toList();
    }
}
