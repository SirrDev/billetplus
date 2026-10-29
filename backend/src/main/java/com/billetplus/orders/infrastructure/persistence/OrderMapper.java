package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.shared.Money;

/**
 * Conversion domaine ↔ entité JPA pour les commandes. Seuls status, payeeLe et
 * motifSansBillet changent après la création : ce sont les seuls champs
 * recopiés par {@link #copierDans}. Les lignes ne sont jamais réécrites.
 */
final class OrderMapper {

    private OrderMapper() {
    }

    static Order versDomaine(OrderJpaEntity entity) {
        return Order.reconstituer(
            entity.getId(),
            entity.getEventId(),
            // Valeur déjà normalisée (CHECK en base) : depuis() la relit à l'identique.
            NumeroTelephone.depuis(entity.getTelephone()),
            entity.getLignes().stream()
                .map(l -> new OrderLine(l.getCategoryId(), l.getQuantite(), Money.ofFcfa(l.getPrixUnitaireFcfa())))
                .toList(),
            entity.getEmpreinteJeton(),
            entity.getCreeLe(),
            entity.getExpireLe(),
            entity.getStatus(),
            entity.getPayeeLe(),
            entity.getMotifSansBillet()
        );
    }

    static OrderJpaEntity versNouvelleEntite(Order order) {
        return new OrderJpaEntity(
            order.getId(),
            order.getEventId(),
            order.getTelephone().valeur(),
            order.getEmpreinteJeton(),
            order.getStatus(),
            order.getCreeLe(),
            order.getExpireLe(),
            order.getPayeeLe(),
            order.getMotifSansBillet(),
            order.getLignes().stream()
                .map(l -> new OrderLineEmbeddable(l.categoryId(), l.quantite(),
                    l.prixUnitaire().valeur().longValueExact()))
                .toList()
        );
    }

    static void copierDans(Order order, OrderJpaEntity entity) {
        if (!order.getId().equals(entity.getId())) {
            throw new IllegalStateException(
                "Identifiants différents : domaine %s, entité %s".formatted(order.getId(), entity.getId()));
        }
        entity.setStatus(order.getStatus());
        entity.setPayeeLe(order.getPayeeLe());
        entity.setMotifSansBillet(order.getMotifSansBillet());
    }
}
