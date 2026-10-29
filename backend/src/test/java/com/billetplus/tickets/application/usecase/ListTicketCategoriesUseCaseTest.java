package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.application.usecase.ListTicketCategoriesUseCase.ResultatCategories;
import com.billetplus.tickets.domain.TicketCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListTicketCategoriesUseCaseTest {

    @Mock
    private TicketCategoryRepository categoryRepository;
    @Mock
    private StatutEvenements statutEvenements;

    private final UUID eventId = UUID.randomUUID();
    private final UUID organisateur = UUID.randomUUID();
    private final List<TicketCategory> categories =
        List.of(new TicketCategory(eventId, "Standard", Money.ofFcfa(5_000), 200));

    private ListTicketCategoriesUseCase useCase() {
        return new ListTicketCategoriesUseCase(categoryRepository, statutEvenements);
    }

    @Test
    void un_visiteur_voit_les_categories_d_un_evenement_ouvert_a_la_vente() {
        when(statutEvenements.estOrganisateur(eventId, null)).thenReturn(false);
        when(statutEvenements.estOuvertALaVente(eventId)).thenReturn(true);
        when(categoryRepository.findByEventId(eventId)).thenReturn(categories);

        ResultatCategories resultat = useCase().execute(eventId, null);

        assertThat(resultat.categories()).isEqualTo(categories);
        assertThat(resultat.vuParLOrganisateur()).isFalse();
    }

    /** Un brouillon reste visible de son organisateur, qui prépare sa billetterie. */
    @Test
    void l_organisateur_voit_les_categories_de_son_brouillon() {
        when(statutEvenements.estOrganisateur(eventId, organisateur)).thenReturn(true);
        when(categoryRepository.findByEventId(eventId)).thenReturn(categories);

        ResultatCategories resultat = useCase().execute(eventId, organisateur);

        assertThat(resultat.vuParLOrganisateur()).isTrue();
        verify(statutEvenements, never()).estOuvertALaVente(eventId);
    }

    @Test
    void un_autre_visiteur_recoit_la_meme_reponse_qu_un_evenement_inconnu_pour_un_brouillon() {
        UUID autre = UUID.randomUUID();
        when(statutEvenements.estOrganisateur(eventId, autre)).thenReturn(false);
        when(statutEvenements.estOuvertALaVente(eventId)).thenReturn(false);

        assertThatThrownBy(() -> useCase().execute(eventId, autre))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Événement introuvable : " + eventId);

        verifyNoInteractions(categoryRepository);
    }
}
