package com.billetplus.tickets.infrastructure.persistence;

import com.billetplus.shared.Money;
import com.billetplus.tickets.domain.TicketCategory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketCategoryMapperTest {

    private TicketCategory categorie() {
        TicketCategory category = new TicketCategory(UUID.randomUUID(), "VIP", Money.ofFcfa(15_000), 100);
        category.reserver(7);
        return category;
    }

    @Test
    void un_aller_retour_domaine_entite_domaine_conserve_tous_les_champs() {
        TicketCategory origine = categorie();

        TicketCategory relue = TicketCategoryMapper.versDomaine(TicketCategoryMapper.versNouvelleEntite(origine));

        assertThat(relue).usingRecursiveComparison().isEqualTo(origine);
    }

    @Test
    void une_nouvelle_entite_n_a_pas_de_version_pour_etre_inseree_et_non_fusionnee() {
        TicketCategoryJpaEntity entity = TicketCategoryMapper.versNouvelleEntite(categorie());

        assertThat(entity.getVersion()).isNull();
    }

    @Test
    void convertit_le_prix_en_fcfa_entiers() {
        TicketCategoryJpaEntity entity = TicketCategoryMapper.versNouvelleEntite(categorie());

        assertThat(entity.getPrixFcfa()).isEqualTo(15_000L);
    }

    @Test
    void copier_dans_reporte_le_quota_vendu_sur_l_entite_existante() {
        TicketCategory category = categorie();
        TicketCategoryJpaEntity entity = TicketCategoryMapper.versNouvelleEntite(category);

        category.reserver(3);
        TicketCategoryMapper.copierDans(category, entity);

        assertThat(entity.getQuotaVendu()).isEqualTo(10);
        assertThat(entity.getId()).isEqualTo(category.getId());
    }

    @Test
    void copier_dans_refuse_une_entite_d_une_autre_categorie() {
        TicketCategoryJpaEntity autre = TicketCategoryMapper.versNouvelleEntite(categorie());

        assertThatThrownBy(() -> TicketCategoryMapper.copierDans(categorie(), autre))
            .isInstanceOf(IllegalStateException.class);
    }
}
