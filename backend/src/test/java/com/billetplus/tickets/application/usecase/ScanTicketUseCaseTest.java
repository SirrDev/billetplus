package com.billetplus.tickets.application.usecase;

import com.billetplus.events.api.publique.StatutEvenements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanTicketUseCaseTest {

    private static final String QR = "payload.signature";

    @Mock
    private ValidateTicketScanUseCase validateScan;
    @Mock
    private StatutEvenements statutEvenements;

    private final UUID eventId = UUID.randomUUID();
    private final UUID utilisateur = UUID.randomUUID();
    private final ResultatScan accepte = ResultatScan.accepte(UUID.randomUUID());

    private ScanTicketUseCase useCase() {
        return new ScanTicketUseCase(validateScan, statutEvenements);
    }

    @Test
    void un_agent_affecte_a_l_evenement_peut_scanner() {
        when(statutEvenements.estAgentAffecte(eventId, utilisateur)).thenReturn(true);
        when(validateScan.execute(QR, eventId)).thenReturn(accepte);

        assertThat(useCase().execute(QR, eventId, utilisateur)).isEqualTo(accepte);
    }

    @Test
    void l_organisateur_de_l_evenement_peut_scanner() {
        when(statutEvenements.estAgentAffecte(eventId, utilisateur)).thenReturn(false);
        when(statutEvenements.estOrganisateur(eventId, utilisateur)).thenReturn(true);
        when(validateScan.execute(QR, eventId)).thenReturn(accepte);

        assertThat(useCase().execute(QR, eventId, utilisateur)).isEqualTo(accepte);
    }

    /** Ni affecté ni organisateur : 403 avant même de lire le QR (aucun billet consommé, aucune information). */
    @Test
    void refuse_un_utilisateur_qui_ne_controle_pas_cet_evenement_sans_lire_le_qr() {
        when(statutEvenements.estAgentAffecte(eventId, utilisateur)).thenReturn(false);
        when(statutEvenements.estOrganisateur(eventId, utilisateur)).thenReturn(false);

        assertThatThrownBy(() -> useCase().execute(QR, eventId, utilisateur))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(validateScan);
    }

    /** Deux agents scannent le même billet : le perdant rejoue une fois et voit DEJA_SCANNE, pas une erreur. */
    @Test
    void rejoue_une_fois_un_conflit_de_version_et_renvoie_le_resultat_du_second_essai() {
        ResultatScan dejaScanne = ResultatScan.dejaScanne(UUID.randomUUID(), Instant.now());
        when(statutEvenements.estAgentAffecte(eventId, utilisateur)).thenReturn(true);
        when(validateScan.execute(QR, eventId))
            .thenThrow(new OptimisticLockingFailureException("scan simultane"))
            .thenReturn(dejaScanne);

        assertThat(useCase().execute(QR, eventId, utilisateur)).isEqualTo(dejaScanne);
        verify(validateScan, times(2)).execute(QR, eventId);
    }

    /** Un seul rejeu : un second conflit remonte (409), jamais une boucle. */
    @Test
    void ne_rejoue_qu_une_fois() {
        when(statutEvenements.estAgentAffecte(eventId, utilisateur)).thenReturn(true);
        when(validateScan.execute(QR, eventId)).thenThrow(new OptimisticLockingFailureException("scan simultane"));

        assertThatThrownBy(() -> useCase().execute(QR, eventId, utilisateur))
            .isInstanceOf(OptimisticLockingFailureException.class);
        verify(validateScan, times(2)).execute(QR, eventId);
    }
}
