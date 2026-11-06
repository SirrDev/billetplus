package com.billetplus.orders.infrastructure.persistence;

import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.Paiement;
import com.billetplus.shared.Money;

/**
 * Conversion domaine ↔ entité JPA pour les commandes. Seuls status, payeeLe,
 * motifSansBillet et le paiement changent après la création : ce sont les seuls
 * champs recopiés par {@link #copierDans}. Les lignes ne sont jamais réécrites.
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
            new TauxCommission(entity.getTauxCommissionPointsBase()),
            entity.getStatus(),
            entity.getPayeeLe(),
            entity.getMotifSansBillet(),
            entity.getPaiementReference() == null ? null : new Paiement(entity.getPaiementMoyen(),
                entity.getPaiementReference(), entity.getPaiementTransactionId(), entity.getPaiementUrl(),
                entity.getPaiementOuvertLe())
        );
    }

    static OrderJpaEntity versNouvelleEntite(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(
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
                .toList(),
            order.getTauxCommission().pointsBase()
        );
        copierPaiement(order, entity);
        return entity;
    }

    private static void copierPaiement(Order order, OrderJpaEntity entity) {
        Paiement paiement = order.getPaiement();
        if (paiement == null) {
            entity.setPaiement(null, null, null, null, null);
        } else {
            entity.setPaiement(paiement.moyen(), paiement.reference(), paiement.transactionId(), paiement.url(),
                paiement.ouvertLe());
        }
    }

    static void copierDans(Order order, OrderJpaEntity entity) {
        if (!order.getId().equals(entity.getId())) {
            throw new IllegalStateException(
                "Identifiants différents : domaine %s, entité %s".formatted(order.getId(), entity.getId()));
        }
        entity.setStatus(order.getStatus());
        entity.setPayeeLe(order.getPayeeLe());
        entity.setMotifSansBillet(order.getMotifSansBillet());
        copierPaiement(order, entity);
    }
}
