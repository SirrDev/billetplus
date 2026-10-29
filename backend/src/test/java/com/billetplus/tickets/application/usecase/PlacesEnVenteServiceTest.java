package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.ReservationTemporaire;
import com.billetplus.tickets.domain.TicketCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlacesEnVenteServiceTest {

    private final TicketCategoryRepository categories = mock(TicketCategoryRepository.class);
    private final StatutEvenements statut = mock(StatutEvenements.class);
    private final ReserveSeatsUseCase reserve = mock(ReserveSeatsUseCase.class);
    private final ReleaseSeatsUseCase release = mock(ReleaseSeatsUseCase.class);
    private final PlacesEnVenteService service = new PlacesEnVenteService(categories, statut, reserve, release);

    private final UUID evenement = UUID.randomUUID();
    private final TicketCategory categorie = new TicketCategory(evenement, "VIP", Money.ofFcfa(15000), 50);

    @Test
    void le_prix_est_donne_pour_une_categorie_de_l_evenement_ouvert_a_la_vente() {
        when(categories.findById(categorie.getId())).thenReturn(Optional.of(categorie));
        when(statut.estOuvertALaVente(evenement)).thenReturn(true);

        assertThat(service.prixSiEnVente(evenement, categorie.getId())).contains(Money.ofFcfa(15000));
    }

    @Test
    void aucun_prix_pour_un_evenement_ferme_une_categorie_d_un_autre_evenement_ou_inconnue() {
        when(categories.findById(categorie.getId())).thenReturn(Optional.of(categorie));
        when(statut.estOuvertALaVente(evenement)).thenReturn(false);
        assertThat(service.prixSiEnVente(evenement, categorie.getId())).isEmpty();

        assertThat(service.prixSiEnVente(UUID.randomUUID(), categorie.getId())).isEmpty();
        assertThat(service.prixSiEnVente(evenement, UUID.randomUUID())).isEmpty();
        assertThat(service.prixSiEnVente(null, categorie.getId())).isEmpty();
        assertThat(service.prixSiEnVente(evenement, null)).isEmpty();
    }

    @Test
    void reserver_et_liberer_deleguent_aux_cas_d_usage_du_module() {
        UUID commande = UUID.randomUUID();
        Instant expiration = Instant.parse("2026-11-01T10:05:00Z");
        when(reserve.execute(categorie.getId(), commande, 2))
            .thenReturn(ReservationTemporaire.reconstituer(categorie.getId(), commande, 2, expiration));

        assertThat(service.reserver(categorie.getId(), commande, 2)).isEqualTo(expiration);
        service.liberer(categorie.getId(), commande);
        verify(release).execute(categorie.getId(), commande);
    }
}
