package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.VenteFermeeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Cas d'usage (niveau 3, voir ADR 0002) — un organisateur crée une catégorie
 * de billets (nom, prix, quota) pour l'un de SES événements. Sans catégorie,
 * rien ne peut être réservé ni vendu.
 *
 * Les invariants (nom obligatoire, prix non négatif, quota strictement positif)
 * vivent dans le domaine ({@link TicketCategory}, {@link Money}). La propriété et
 * l'état de l'événement sont lus par le port public du module events (ADR 0005).
 */
@Service
public class CreateTicketCategoryUseCase {

    private final TicketCategoryRepository categoryRepository;
    private final StatutEvenements statutEvenements;

    public CreateTicketCategoryUseCase(TicketCategoryRepository categoryRepository,
                                       StatutEvenements statutEvenements) {
        this.categoryRepository = categoryRepository;
        this.statutEvenements = statutEvenements;
    }

    /**
     * @param organisateurId l'utilisateur authentifié (tiré du jeton, jamais de la requête)
     * @throws IllegalArgumentException si un paramètre est absent, le prix négatif ou le quota nul
     * @throws NoSuchElementException   si l'événement n'existe pas OU n'appartient pas à
     *                                  l'organisateur : même réponse, pour ne révéler ni
     *                                  l'existence d'un brouillon, ni son propriétaire
     * @throws VenteFermeeException     si l'événement est annulé ou terminé
     */
    @Transactional
    public TicketCategory execute(UUID eventId, UUID organisateurId, String nom, long prixFcfa, int quotaTotal) {
        if (eventId == null || organisateurId == null) {
            throw new IllegalArgumentException("eventId et organisateurId sont obligatoires");
        }
        if (!statutEvenements.estOrganisateur(eventId, organisateurId)) {
            // Même message que EventService.get : réponse indiscernable d'un identifiant inconnu.
            throw new NoSuchElementException("Événement introuvable : " + eventId);
        }
        if (!statutEvenements.accepteDesCategories(eventId)) {
            throw new VenteFermeeException();
        }
        TicketCategory category = new TicketCategory(eventId, nom, Money.ofFcfa(prixFcfa), quotaTotal);
        return categoryRepository.save(category);
    }
}
