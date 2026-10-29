package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.TicketCategory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Lecture (niveau 1 en pratique, voir ADR 0002) — catégories de billets d'un
 * événement, pour un acheteur ou pour l'organisateur.
 *
 * Même règle de visibilité que le module events : les catégories d'un
 * événement ouvert à la vente sont publiques ; celles d'un brouillon (ou d'un
 * événement annulé, terminé) ne sont visibles que de son organisateur. Tous
 * les autres reçoivent la même réponse qu'un événement inconnu.
 *
 * Le cas d'usage renvoie le domaine complet ; c'est la couche api qui décide
 * de ce qu'elle montre (un acheteur ne voit pas le stock, l'organisateur oui).
 */
@Service
public class ListTicketCategoriesUseCase {

    private final TicketCategoryRepository categoryRepository;
    private final StatutEvenements statutEvenements;

    public ListTicketCategoriesUseCase(TicketCategoryRepository categoryRepository,
                                       StatutEvenements statutEvenements) {
        this.categoryRepository = categoryRepository;
        this.statutEvenements = statutEvenements;
    }

    /**
     * @param viewerId l'utilisateur authentifié, ou null pour un visiteur anonyme
     * @throws NoSuchElementException si l'événement est inconnu, ou non visible par ce visiteur
     */
    public ResultatCategories execute(UUID eventId, UUID viewerId) {
        if (eventId == null) {
            throw new NoSuchElementException("Événement introuvable : null");
        }
        boolean organisateur = statutEvenements.estOrganisateur(eventId, viewerId);
        if (!organisateur && !statutEvenements.estOuvertALaVente(eventId)) {
            throw new NoSuchElementException("Événement introuvable : " + eventId);
        }
        return new ResultatCategories(categoryRepository.findByEventId(eventId), organisateur);
    }

    /**
     * @param vuParLOrganisateur vrai si le demandeur est l'organisateur de l'événement :
     *                           la couche api peut alors montrer les quotas
     */
    public record ResultatCategories(List<TicketCategory> categories, boolean vuParLOrganisateur) {
    }
}
