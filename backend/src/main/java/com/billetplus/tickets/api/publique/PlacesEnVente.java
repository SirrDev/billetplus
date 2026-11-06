package com.billetplus.tickets.api.publique;

import com.billetplus.shared.Money;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port public du module tickets (ADR 0005) : le seul point d'entrée par lequel
 * un autre module (orders) lit le prix d'une catégorie et bloque ou rend des
 * places. Implémenté à l'intérieur du module tickets.
 *
 * Uniquement des types du JDK et de shared : la règle ArchUnit
 * ports_publics_ne_contiennent_que_des_interfaces interdit tout record ici.
 */
public interface PlacesEnVente {

    /**
     * Prix unitaire de la catégorie, si elle existe, appartient à {@code eventId},
     * et que l'événement est ouvert à la vente. Vide dans tous les autres cas
     * (même réponse : un brouillon reste indiscernable d'un identifiant inconnu).
     */
    Optional<Money> prixSiEnVente(UUID eventId, UUID categoryId);

    /**
     * Bloque {@code quantite} places pour la commande, jusqu'à l'instant renvoyé
     * (réservation temporaire, ADR 0003). Contrat de l'appelant : voir
     * ReserveSeatsUseCase.
     *
     * @throws java.util.NoSuchElementException si la catégorie est inconnue ou plus en vente (404)
     * @throws com.billetplus.shared.exception.BusinessConflictException si les places manquent (409)
     */
    Instant reserver(UUID categoryId, UUID orderId, int quantite);

    /**
     * Places gratuites de l'événement : somme des quotas de ses catégories à 0 FCFA, seules
     * (tarif de l'entrée libre, ADR 0010 ; des places payantes ne changent pas ce tarif).
     * Zéro s'il n'en a aucune.
     */
    long placesGratuites(UUID eventId);

    /** Rend les places de la commande. Idempotent. Contrat de l'appelant : voir ReleaseSeatsUseCase. */
    void liberer(UUID categoryId, UUID orderId);
}
