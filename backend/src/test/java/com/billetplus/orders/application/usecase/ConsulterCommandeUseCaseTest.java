package com.billetplus.orders.application.usecase;

import com.billetplus.orders.domain.TauxCommission;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.domain.NumeroTelephone;
import com.billetplus.orders.domain.Order;
import com.billetplus.orders.domain.OrderLine;
import com.billetplus.shared.Money;
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
import static org.mockito.Mockito.when;

class ConsulterCommandeUseCaseTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ConsulterCommandeUseCase useCase = new ConsulterCommandeUseCase(orderRepository);

    private Order.CommandeCreee commandeEnregistree() {
        Order.CommandeCreee creee = Order.creer(UUID.randomUUID(), NumeroTelephone.depuis("771234567"),
            List.of(new OrderLine(UUID.randomUUID(), 1, Money.ofFcfa(5000))), Instant.now(), Duration.ofMinutes(4), TauxCommission.CINQ_POUR_CENT);
        when(orderRepository.findById(creee.commande().getId())).thenReturn(Optional.of(creee.commande()));
        return creee;
    }

    @Test
    void le_bon_jeton_ouvre_la_commande() {
        Order.CommandeCreee creee = commandeEnregistree();

        assertThat(useCase.execute(creee.commande().getId(), creee.jeton().enClair())).isSameAs(creee.commande());
    }

    @Test
    void commande_inconnue_jeton_absent_ou_faux_donnent_la_meme_reponse() {
        Order.CommandeCreee creee = commandeEnregistree();
        UUID id = creee.commande().getId();

        assertThatThrownBy(() -> useCase.execute(id, "faux"))
            .isInstanceOf(NoSuchElementException.class).hasMessage("Commande introuvable");
        assertThatThrownBy(() -> useCase.execute(id, null))
            .isInstanceOf(NoSuchElementException.class).hasMessage("Commande introuvable");
        // L'empreinte stockée n'ouvre pas la commande.
        assertThatThrownBy(() -> useCase.execute(id, creee.commande().getEmpreinteJeton()))
            .isInstanceOf(NoSuchElementException.class).hasMessage("Commande introuvable");
        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), creee.jeton().enClair()))
            .isInstanceOf(NoSuchElementException.class).hasMessage("Commande introuvable");
        assertThatThrownBy(() -> useCase.execute(null, creee.jeton().enClair()))
            .isInstanceOf(NoSuchElementException.class).hasMessage("Commande introuvable");
    }
}
