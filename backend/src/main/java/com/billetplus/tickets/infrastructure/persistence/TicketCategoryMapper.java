package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.shared.Money;
import com.billetplus.tickets.domain.TicketCategory;

/**
 * Conversion domaine ↔ entité JPA pour les catégories de billets.
 *
 * Trois opérations distinctes, pour ne jamais perdre la colonne version :
 * - versDomaine : reconstruction sans revalidation (TicketCategory.reconstituer) ;
 * - versNouvelleEntite : création uniquement (version null → INSERT) ;
 * - copierDans : mise à jour d'une entité déjà gérée par Hibernate, qui
 *   conserve et contrôle sa version. Seul quotaVendu est mutable dans le domaine.
 */
final class TicketCategoryMapper {

    private TicketCategoryMapper() {
    }

    static TicketCategory versDomaine(TicketCategoryJpaEntity entity) {
        return TicketCategory.reconstituer(
            entity.getId(),
            entity.getEventId(),
            entity.getNom(),
            Money.ofFcfa(entity.getPrixFcfa()),
            entity.getQuotaTotal(),
            entity.getQuotaVendu()
        );
    }

    static TicketCategoryJpaEntity versNouvelleEntite(TicketCategory category) {
        return new TicketCategoryJpaEntity(
            category.getId(),
            category.getEventId(),
            category.getNom(),
            // longValueExact : échoue au lieu de tronquer un montant hors BIGINT.
            category.getPrix().valeur().longValueExact(),
            category.getQuotaTotal(),
            category.getQuotaVendu()
        );
    }

    static void copierDans(TicketCategory category, TicketCategoryJpaEntity entity) {
        if (!category.getId().equals(entity.getId())) {
            throw new IllegalStateException(
                "Identifiants différents : domaine %s, entité %s".formatted(category.getId(), entity.getId()));
        }
        entity.setQuotaVendu(category.getQuotaVendu());
    }
}
