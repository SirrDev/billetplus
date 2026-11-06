package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.shared.Money;
import com.billetplus.tickets.api.publique.PlacesEnVente;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.TicketCategory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port public {@link PlacesEnVente} (ADR 0005). Aucune règle
 * propre : elle délègue aux cas d'usage du module, pour que orders passe par
 * les mêmes contrôles que n'importe quel autre appelant.
 */
@Service
class PlacesEnVenteService implements PlacesEnVente {

    private final TicketCategoryRepository categoryRepository;
    private final StatutEvenements statutEvenements;
    private final ReserveSeatsUseCase reserveSeats;
    private final ReleaseSeatsUseCase releaseSeats;

    PlacesEnVenteService(TicketCategoryRepository categoryRepository, StatutEvenements statutEvenements,
                         ReserveSeatsUseCase reserveSeats, ReleaseSeatsUseCase releaseSeats) {
        this.categoryRepository = categoryRepository;
        this.statutEvenements = statutEvenements;
        this.reserveSeats = reserveSeats;
        this.releaseSeats = releaseSeats;
    }

    @Override
    public Optional<Money> prixSiEnVente(UUID eventId, UUID categoryId) {
        if (eventId == null || categoryId == null) {
            return Optional.empty();
        }
        return categoryRepository.findById(categoryId)
            .filter(c -> c.getEventId().equals(eventId))
            .filter(c -> statutEvenements.estOuvertALaVente(eventId))
            .map(TicketCategory::getPrix);
    }

    @Override
    public Instant reserver(UUID categoryId, UUID orderId, int quantite) {
        return reserveSeats.execute(categoryId, orderId, quantite).getExpireLe();
    }

    @Override
    public long placesTotales(UUID eventId) {
        return categoryRepository.findByEventId(eventId).stream()
            .mapToLong(TicketCategory::getQuotaTotal)
            .sum();
    }

    @Override
    public void liberer(UUID categoryId, UUID orderId) {
        releaseSeats.execute(categoryId, orderId);
    }
}
