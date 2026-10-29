package com.billetplus.tickets.domain;

import com.billetplus.shared.Money;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;

import java.util.UUID;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — aucune annotation JPA, aucune
 * dépendance à l'infrastructure. Testable en quelques millisecondes.
 *
 * L'entité JPA correspondante (TicketCategoryJpaEntity) et le mapper associé
 * vivent dans infrastructure/persistence/ (voir feature/backend-tickets-jpa-mapper).
 */
public class TicketCategory {

    private final UUID id;
    private final UUID eventId;
    private final String nom;
    private final Money prix;
    private final int quotaTotal;
    private int quotaVendu;

    public TicketCategory(UUID eventId, String nom, Money prix, int quotaTotal) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId est obligatoire");
        }
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom de la catégorie est obligatoire");
        }
        if (prix == null) {
            throw new IllegalArgumentException("Le prix est obligatoire (Money.ofFcfa(0) pour une catégorie gratuite)");
        }
        if (quotaTotal <= 0) {
            throw new IllegalArgumentException("Le quota total doit être strictement positif");
        }
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.nom = nom;
        this.prix = prix;
        this.quotaTotal = quotaTotal;
        this.quotaVendu = 0;
    }

    /**
     * Reconstruction depuis la persistance — ne revalide pas les invariants
     * de création, l'état est supposé déjà cohérent en base.
     */
    public static TicketCategory reconstituer(UUID id, UUID eventId, String nom, Money prix,
                                               int quotaTotal, int quotaVendu) {
        TicketCategory category = new TicketCategory(id, eventId, nom, prix, quotaTotal);
        category.quotaVendu = quotaVendu;
        return category;
    }

    private TicketCategory(UUID id, UUID eventId, String nom, Money prix, int quotaTotal) {
        this.id = id;
        this.eventId = eventId;
        this.nom = nom;
        this.prix = prix;
        this.quotaTotal = quotaTotal;
    }

    /**
     * Réserve {@code quantite} billets sur le quota disponible.
     *
     * Ce verrou optimiste au niveau objet protège la cohérence en mémoire ;
     * sous forte concurrence, le mécanisme réel de protection est porté par
     * l'infrastructure (verrouillage optimiste JPA @Version ou requête SQL
     * atomique — voir ADR 0003 et feature/backend-tickets-quota-lock). Cette
     * méthode reste la source de vérité métier, appelée dans les deux cas.
     *
     * @throws QuotaDepasseException si la quantité demandée dépasse le quota restant
     */
    public void reserver(int quantite) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantité à réserver doit être strictement positive");
        }
        int quotaRestant = quotaTotal - quotaVendu;
        if (quantite > quotaRestant) {
            throw new QuotaDepasseException(id, quantite, quotaRestant);
        }
        quotaVendu += quantite;
    }

    /**
     * Libère {@code quantite} places suite à une annulation/un remboursement.
     */
    public void libererQuota(int quantite) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantité à libérer doit être strictement positive");
        }
        quotaVendu = Math.max(0, quotaVendu - quantite);
    }

    public int quotaRestant() {
        return quotaTotal - quotaVendu;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getNom() {
        return nom;
    }

    public Money getPrix() {
        return prix;
    }

    public int getQuotaTotal() {
        return quotaTotal;
    }

    public int getQuotaVendu() {
        return quotaVendu;
    }
}
