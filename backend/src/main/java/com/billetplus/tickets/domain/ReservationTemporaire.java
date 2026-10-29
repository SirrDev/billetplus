package com.billetplus.tickets.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Domaine pur (niveau 3, voir ADR 0002) — places d'une catégorie bloquées pour
 * une commande pendant le tunnel de paiement, jusqu'à {@link #getExpireLe()}.
 *
 * Voir ADR 0003 : la réservation vit dans Redis et expire d'elle-même ; si le
 * paiement échoue ou n'aboutit pas, les places redeviennent disponibles sans
 * job de nettoyage. Elle ne modifie jamais ticket_categories : seule une vente
 * payée consomme le quota (TicketCategory.reserver).
 *
 * Une commande a au plus une réservation par catégorie : réserver de nouveau
 * pour la même commande remplace la réservation précédente.
 */
public final class ReservationTemporaire {

    private final UUID categoryId;
    private final UUID orderId;
    private final int quantite;
    private final Instant expireLe;

    private ReservationTemporaire(UUID categoryId, UUID orderId, int quantite, Instant expireLe) {
        this.categoryId = categoryId;
        this.orderId = orderId;
        this.quantite = quantite;
        this.expireLe = expireLe;
    }

    /**
     * @param maintenant instant de la demande (horloge de l'application)
     * @param duree      durée de validité, strictement positive
     * @throws IllegalArgumentException si un paramètre est absent, si la quantité n'est pas
     *                                  comprise entre 1 et {@link QuantiteParCommande#MAX}
     *                                  ou si la durée n'est pas strictement positive
     */
    public static ReservationTemporaire creer(UUID categoryId, UUID orderId, int quantite,
                                              Instant maintenant, Duration duree) {
        if (categoryId == null || orderId == null || maintenant == null || duree == null) {
            throw new IllegalArgumentException("categoryId, orderId, maintenant et duree sont obligatoires");
        }
        QuantiteParCommande.verifier(quantite);
        if (duree.isZero() || duree.isNegative()) {
            throw new IllegalArgumentException("La durée de réservation doit être strictement positive");
        }
        // À la milliseconde : l'expiration sert de score dans Redis (epoch en ms).
        Instant expireLe = maintenant.plus(duree).truncatedTo(ChronoUnit.MILLIS);
        return new ReservationTemporaire(categoryId, orderId, quantite, expireLe);
    }

    /**
     * Reconstruction depuis le stockage — ne revalide pas les invariants de
     * création, l'état est supposé déjà cohérent.
     */
    public static ReservationTemporaire reconstituer(UUID categoryId, UUID orderId, int quantite,
                                                     Instant expireLe) {
        return new ReservationTemporaire(categoryId, orderId, quantite, expireLe);
    }

    /** Une réservation est expirée à partir de son instant d'expiration (inclus). */
    public boolean estExpireeA(Instant instant) {
        return !instant.isBefore(expireLe);
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public int getQuantite() {
        return quantite;
    }

    public Instant getExpireLe() {
        return expireLe;
    }
}
