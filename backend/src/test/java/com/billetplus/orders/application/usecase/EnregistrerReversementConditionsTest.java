package com.billetplus.orders.application.usecase;

import com.billetplus.auth.api.publique.Comptes;
import com.billetplus.events.api.publique.StatutEvenements;
import com.billetplus.orders.application.port.OrderRepository;
import com.billetplus.orders.application.port.ReversementRepository;
import com.billetplus.orders.domain.Reversement;
import com.billetplus.shared.evenements.ReversementEnregistre;
import com.billetplus.shared.exception.BusinessConflictException;
import com.billetplus.tickets.api.publique.ControleEntrees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnregistrerReversementConditionsTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-10T10:00:00Z");
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 10);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ReversementRepository reversementRepository;
    @Mock
    private StatutEvenements statutEvenements;
    @Mock
    private Comptes comptes;
    @Mock
    private ControleEntrees controleEntrees;
    @Mock
    private ApplicationEventPublisher publisher;

    private final UUID eventId = UUID.randomUUID();
    private final UUID organisateur = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    private EnregistrerReversementUseCase useCase;

    @BeforeEach
    void preparer() {
        Clock clock = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        useCase = new EnregistrerReversementUseCase(orderRepository, reversementRepository, statutEvenements,
            comptes, controleEntrees, publisher, clock);
        lenient().when(orderRepository.existeCommandePayee(eventId)).thenReturn(true);
        lenient().when(reversementRepository.save(any(Reversement.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(statutEvenements.organisateurDe(eventId)).thenReturn(Optional.of(organisateur));
        lenient().when(statutEvenements.estAnnule(eventId)).thenReturn(false);
        lenient().when(statutEvenements.finDe(eventId)).thenReturn(Optional.of(Instant.parse("2026-11-09T20:00:00Z")));
        lenient().when(comptes.estOrganisateurVerifie(organisateur)).thenReturn(true);
        lenient().when(controleEntrees.billetsScannes(eventId)).thenReturn(12L);
    }

    private Reversement virement(String derogation) {
        return useCase.execute(eventId, 50_000, "WAVE-1", null, AUJOURDHUI, admin, derogation);
    }

    @Test
    void accepte_un_virement_quand_toutes_les_conditions_sont_reunies() {
        Reversement r = virement(null);

        assertThat(r.getMontantFcfa()).isEqualTo(50_000);
        assertThat(r.getDerogationScans()).isNull();
        verify(reversementRepository).save(any(Reversement.class));
    }

    @Test
    void refuse_un_virement_positif_si_l_organisateur_n_est_pas_verifie() {
        lenient().when(comptes.estOrganisateurVerifie(organisateur)).thenReturn(false);

        assertThatThrownBy(() -> virement(null)).isInstanceOf(BusinessConflictException.class);

        verify(reversementRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void refuse_si_l_evenement_s_est_termine_aujourd_hui_et_indique_la_date_permise() {
        lenient().when(statutEvenements.finDe(eventId)).thenReturn(Optional.of(Instant.parse("2026-11-10T08:00:00Z")));

        assertThatThrownBy(() -> virement(null))
            .isInstanceOf(BusinessConflictException.class)
            .hasMessageContaining("2026-11-11");

        verify(reversementRepository, never()).save(any());
    }

    @Test
    void accepte_si_l_evenement_s_est_termine_hier() {
        lenient().when(statutEvenements.finDe(eventId)).thenReturn(Optional.of(Instant.parse("2026-11-09T23:59:00Z")));

        Reversement r = virement(null);

        assertThat(r.getMontantFcfa()).isEqualTo(50_000);
    }

    @Test
    void refuse_sans_billet_scanne_et_sans_derogation() {
        lenient().when(controleEntrees.billetsScannes(eventId)).thenReturn(0L);

        assertThatThrownBy(() -> virement(null)).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> virement("   ")).isInstanceOf(BusinessConflictException.class);

        verify(reversementRepository, never()).save(any());
    }

    @Test
    void accepte_sans_billet_scanne_avec_derogation_enregistree() {
        lenient().when(controleEntrees.billetsScannes(eventId)).thenReturn(0L);

        Reversement r = virement("Entree sans controle, valide par telephone");

        assertThat(r.getDerogationScans()).isEqualTo("Entree sans controle, valide par telephone");
    }

    @Test
    void une_correction_negative_reste_acceptee_meme_non_verifie_avant_le_lendemain_et_sans_scan() {
        lenient().when(comptes.estOrganisateurVerifie(organisateur)).thenReturn(false);
        lenient().when(statutEvenements.finDe(eventId)).thenReturn(Optional.of(Instant.parse("2026-11-10T08:00:00Z")));
        lenient().when(controleEntrees.billetsScannes(eventId)).thenReturn(0L);

        Reversement r = useCase.execute(eventId, -5_000, "WAVE-2", "Erreur de saisie", AUJOURDHUI, admin,
            "derogation inutile");

        assertThat(r.getMontantFcfa()).isEqualTo(-5_000);
        assertThat(r.getDerogationScans()).isNull();
    }

    @Test
    void publie_reversement_enregistre_avec_l_organisateur_et_la_date() {
        Reversement r = virement(null);

        verify(publisher).publishEvent(new ReversementEnregistre(organisateur, r.getEnregistreLe()));
        assertThat(r.getEnregistreLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void evenement_inconnu_leve_une_erreur_et_n_enregistre_rien() {
        lenient().when(statutEvenements.organisateurDe(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> virement(null)).isInstanceOf(NoSuchElementException.class);

        verify(reversementRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
