package com.billetplus.tickets.domain;

import com.billetplus.shared.Money;
import com.billetplus.tickets.domain.exception.QuotaDepasseException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketCategoryTest {

    private TicketCategory categorie(int quotaTotal) {
        return new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), quotaTotal);
    }

    @Test
    void reserve_la_quantite_demandee_quand_le_quota_le_permet() {
        TicketCategory category = categorie(100);

        category.reserver(30);

        assertThat(category.getQuotaVendu()).isEqualTo(30);
        assertThat(category.quotaRestant()).isEqualTo(70);
    }

    @Test
    void refuse_une_reservation_qui_depasserait_le_quota() {
        TicketCategory category = categorie(10);
        category.reserver(8);

        assertThatThrownBy(() -> category.reserver(5))
            .isInstanceOf(QuotaDepasseException.class);

        // L'état n'est pas modifié par la tentative refusée — point critique
        // pour l'anti-overselling : une exception ne doit jamais laisser
        // l'objet dans un état partiellement appliqué.
        assertThat(category.getQuotaVendu()).isEqualTo(8);
    }

    @Test
    void accepte_une_reservation_qui_consomme_exactement_le_quota_restant() {
        TicketCategory category = categorie(10);
        category.reserver(7);

        category.reserver(3);

        assertThat(category.quotaRestant()).isZero();
    }

    @Test
    void refuse_une_quantite_negative_ou_nulle() {
        TicketCategory category = categorie(10);

        assertThatThrownBy(() -> category.reserver(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> category.reserver(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void libere_le_quota_suite_a_une_annulation() {
        TicketCategory category = categorie(10);
        category.reserver(5);

        category.libererQuota(2);

        assertThat(category.getQuotaVendu()).isEqualTo(3);
        assertThat(category.quotaRestant()).isEqualTo(7);
    }

    @Test
    void refuse_de_creer_une_categorie_avec_un_quota_total_invalide() {
        assertThatThrownBy(() -> new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(1000), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
