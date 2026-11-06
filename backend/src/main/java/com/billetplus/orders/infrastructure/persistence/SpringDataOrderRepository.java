package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface Spring Data — usage interne à infrastructure/persistence uniquement.
 * Le reste de l'application passe par le port OrderRepository.
 */
interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {

    /** Les lignes sont chargées dans la même requête (relation LAZY, open-in-view: false). */
    @Override
    @EntityGraph(attributePaths = "lignes")
    Optional<OrderJpaEntity> findById(UUID id);

    /** Servie par idx_orders_telephone_cree_le (V8), préfixe telephone. */
    long countByTelephoneAndEventIdAndStatusAndExpireLeAfter(String telephone, UUID eventId, OrderStatus status,
                                                             Instant maintenant);

    /** Servie par l'index unique uq_orders_paiement_reference (V9). */
    @EntityGraph(attributePaths = "lignes")
    Optional<OrderJpaEntity> findByPaiementReference(String reference);

    @EntityGraph(attributePaths = "lignes")
    List<OrderJpaEntity> findByStatusOrderByPayeeLeAsc(OrderStatus status, Pageable pageable);

    @Query(
        "select distinct o.eventId from OrderJpaEntity o where o.status = :status")
    List<UUID> evenementsAvecStatut(@Param("status") OrderStatus status);

    /**
     * Totaux par événement (ADR 0009) : la sous-requête regroupe par commande, pour que la
     * commission soit arrondie au franc inférieur commande par commande (division entière
     * de PostgreSQL sur des montants positifs = plancher), comme TauxCommission.commissionSur.
     * Le total de chaque commande est reconverti en BIGINT : sum() rend un numeric, et
     * numeric / 10000 garderait des décimales au lieu d'arrondir au franc inférieur.
     * Lignes : event_id, commandes, brut, commission.
     */
    @Query(value = "SELECT t.event_id, count(*), coalesce(sum(t.total), 0), coalesce(sum(t.total * t.taux / 10000), 0) "
        + "FROM (SELECT o.id, o.event_id, o.taux_commission_points_base AS taux, "
        + "CAST(sum(CAST(l.quantite AS BIGINT) * l.prix_unitaire_fcfa) AS BIGINT) AS total "
        + "FROM orders o JOIN order_lines l ON l.order_id = o.id "
        + "WHERE o.status = 'PAYEE' AND o.event_id = :eventId "
        + "GROUP BY o.id, o.event_id, o.taux_commission_points_base) t GROUP BY t.event_id", nativeQuery = true)
    List<Object[]> totauxPayees(@Param("eventId") UUID eventId);

    @Query(value = "SELECT t.event_id, count(*), coalesce(sum(t.total), 0), coalesce(sum(t.total * t.taux / 10000), 0) "
        + "FROM (SELECT o.id, o.event_id, o.taux_commission_points_base AS taux, "
        + "CAST(sum(CAST(l.quantite AS BIGINT) * l.prix_unitaire_fcfa) AS BIGINT) AS total "
        + "FROM orders o JOIN order_lines l ON l.order_id = o.id "
        + "WHERE o.status = 'PAYEE' "
        + "GROUP BY o.id, o.event_id, o.taux_commission_points_base) t GROUP BY t.event_id", nativeQuery = true)
    List<Object[]> totauxPayeesParEvenement();

    /** Servie par idx_orders_entree_libre_telephone (V17). */
    @Query(value = "SELECT CAST(coalesce(sum(l.quantite), 0) AS BIGINT) FROM orders o "
        + "JOIN order_lines l ON l.order_id = o.id "
        + "WHERE o.telephone = :telephone AND o.event_id = :eventId AND o.tarif_entree_libre_fcfa IS NOT NULL",
        nativeQuery = true)
    long compterBilletsGratuits(@Param("telephone") String telephone, @Param("eventId") UUID eventId);

    /** Servie par idx_orders_event_id_status (V8). */
    boolean existsByEventIdAndStatus(UUID eventId, OrderStatus status);

    /** Servie par idx_orders_event_id_status (V8). */
    @EntityGraph(attributePaths = "lignes")
    List<OrderJpaEntity> findByEventIdAndStatusOrderByPayeeLeAsc(UUID eventId, OrderStatus status, Pageable pageable);
}
