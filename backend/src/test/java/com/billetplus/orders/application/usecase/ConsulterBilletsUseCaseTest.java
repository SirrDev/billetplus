package com.billetplus.orders.application.usecase;

import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.orders.domain.exception.TransitionCommandeInterditeException;
import com.billetplus.shared.Money;
import com.billetplus.tickets.api.publique.BilletVendu;
import com.billetplus.tickets.api.publique.BilletsDeCommande;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsulterBilletsUseCaseTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final BilletsDeCommande billets = mock(BilletsDeCommande.class);
    private final ConsulterBilletsUseCase useCase =
        new ConsulterBilletsUseCase(new ConsulterCommandeUseCase(orderRepository), billets);

    private Order.CommandeCreee commande() {
        Order.CommandeCreee creee = Order.creer(UUID.randomUUID(), NumeroTelephone.depuis("771234567"),
            List.of(new OrderLine(UUID.randomUUID(), 1, Money.ofFcfa(5000))), Instant.now(), Duration.ofMinutes(4));
        when(orderRepository.findById(creee.commande().getId())).thenReturn(Optional.of(creee.commande()));
        return creee;
    }

    @Test
    void une_commande_payee_donne_ses_billets() {
        Order.CommandeCreee creee = commande();
        creee.commande().marquerPayee(Instant.now());
        List<BilletVendu> attendus = List.of(mock(BilletVendu.class));
        when(billets.billetsDe(creee.commande().getId())).thenReturn(attendus);

        assertThat(useCase.execute(creee.commande().getId(), creee.jeton().enClair())).isSameAs(attendus);
    }

    @Test
    void une_commande_pas_encore_payee_ou_a_rembourser_ne_donne_aucun_billet() {
        Order.CommandeCreee enAttente = commande();
        assertThatThrownBy(() -> useCase.execute(enAttente.commande().getId(), enAttente.jeton().enClair()))
            .isInstanceOf(TransitionCommandeInterditeException.class);

        Order.CommandeCreee sansBillet = commande();
        sansBillet.commande().marquerPayeeSansBillet(com.billetplus.orders.domain.MotifSansBillet.VENTE_FERMEE, Instant.now());
        assertThatThrownBy(() -> useCase.execute(sansBillet.commande().getId(), sansBillet.jeton().enClair()))
            .isInstanceOf(TransitionCommandeInterditeException.class);
        verifyNoInteractions(billets);
    }

    @Test
    void sans_le_bon_jeton_rien_n_est_lu_des_billets() {
        Order.CommandeCreee creee = commande();
        creee.commande().marquerPayee(Instant.now());

        assertThatThrownBy(() -> useCase.execute(creee.commande().getId(), "faux"))
            .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(billets);
    }
}
