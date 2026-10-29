package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.shared.Money;
import com.billetplus.tickets.application.port.TicketCategoryRepository;
import com.billetplus.tickets.domain.TicketCategory;
import com.billetplus.tickets.domain.exception.VenteFermeeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTicketCategoryUseCaseTest {

    @Mock
    private TicketCategoryRepository categoryRepository;
    @Mock
    private StatutEvenements statutEvenements;

    private final UUID eventId = UUID.randomUUID();
    private final UUID organisateur = UUID.randomUUID();

    private CreateTicketCategoryUseCase useCase() {
        return new CreateTicketCategoryUseCase(categoryRepository, statutEvenements);
    }

    @Test
    void cree_la_categorie_de_l_evenement_de_l_organisateur() {
        when(statutEvenements.estOrganisateur(eventId, organisateur)).thenReturn(true);
        when(statutEvenements.accepteDesCategories(eventId)).thenReturn(true);
        when(categoryRepository.save(any(TicketCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketCategory category = useCase().execute(eventId, organisateur, "VIP", 15_000, 100);

        assertThat(category.getEventId()).isEqualTo(eventId);
        assertThat(category.getNom()).isEqualTo("VIP");
        assertThat(category.getPrix()).isEqualTo(Money.ofFcfa(15_000));
        assertThat(category.getQuotaTotal()).isEqualTo(100);
        assertThat(category.getQuotaVendu()).isZero();
        verify(categoryRepository).save(category);
    }

    @Test
    void accepte_une_categorie_gratuite() {
        when(statutEvenements.estOrganisateur(eventId, organisateur)).thenReturn(true);
        when(statutEvenements.accepteDesCategories(eventId)).thenReturn(true);
        when(categoryRepository.save(any(TicketCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(useCase().execute(eventId, organisateur, "Invitation", 0, 50).getPrix()).isEqualTo(Money.ofFcfa(0));
    }

    /** Un autre organisateur, ou un événement inconnu : même réponse, rien n'est révélé ni écrit. */
    @Test
    void refuse_un_evenement_qui_n_appartient_pas_a_l_organisateur_comme_un_evenement_inconnu() {
        when(statutEvenements.estOrganisateur(eventId, organisateur)).thenReturn(false);

        assertThatThrownBy(() -> useCase().execute(eventId, organisateur, "VIP", 15_000, 100))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Événement introuvable : " + eventId);

        verify(statutEvenements, never()).accepteDesCategories(any());
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void refuse_d_ajouter_une_categorie_a_un_evenement_annule_ou_termine() {
        when(statutEvenements.estOrganisateur(eventId, organisateur)).thenReturn(true);
        when(statutEvenements.accepteDesCategories(eventId)).thenReturn(false);

        assertThatThrownBy(() -> useCase().execute(eventId, organisateur, "VIP", 15_000, 100))
            .isInstanceOf(VenteFermeeException.class);

        verifyNoInteractions(categoryRepository);
    }

    /** Les invariants restent ceux du domaine (TicketCategory, Money) : 400, rien d'écrit. */
    @Test
    void refuse_un_nom_vide_un_prix_negatif_ou_un_quota_nul() {
        when(statutEvenements.estOrganisateur(eventId, organisateur)).thenReturn(true);
        when(statutEvenements.accepteDesCategories(eventId)).thenReturn(true);

        assertThatThrownBy(() -> useCase().execute(eventId, organisateur, " ", 15_000, 100))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(eventId, organisateur, "VIP", -1, 100))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(eventId, organisateur, "VIP", 15_000, 0))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(categoryRepository);
    }

    @Test
    void exige_l_evenement_et_l_organisateur() {
        assertThatThrownBy(() -> useCase().execute(null, organisateur, "VIP", 1, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().execute(eventId, null, "VIP", 1, 1))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(statutEvenements, categoryRepository);
    }
}
