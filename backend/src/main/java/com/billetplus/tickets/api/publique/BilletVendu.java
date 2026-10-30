package com.billetplus.tickets.api.publique;

import java.util.Optional;
import java.util.UUID;

/**
 * Un billet d'une commande, tel que le module tickets le donne au module orders
 * (ADR 0005). Interface et non record : la règle ArchUnit
 * ports_publics_ne_contiennent_que_des_interfaces l'impose dans api.publique.
 */
public interface BilletVendu {

    UUID id();

    UUID categoryId();

    String categoryNom();

    /** EMIS, SCANNE, ANNULE ou REMBOURSE. */
    String statut();

    /**
     * Contenu signé du QR code, seulement pour un billet encore valable (EMIS). Vide
     * pour un billet déjà scanné, annulé ou remboursé : le présenter ne servirait à rien.
     * C'est un jeton porteur : ne jamais le journaliser.
     */
    Optional<String> qr();
}
