package com.billetplus.auth.application;

import com.billetplus.auth.domain.PieceOrganisateur;
import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.StatutVerification;
import com.billetplus.auth.domain.TypePiece;
import com.billetplus.auth.domain.User;
import com.billetplus.auth.domain.VerificationOrganisateur;
import com.billetplus.auth.domain.VerificationOrganisateur.TypeTitulaire;
import com.billetplus.shared.evenements.CompteSuspendu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-10T10:00:00Z");
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    private static final byte[] CHIFFRE = {1, 2, 3, 4};

    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private PieceRepository pieceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChiffrementPieces chiffrement;
    @Mock
    private ApplicationEventPublisher publisher;

    private VerificationService service;
    private User organisateur;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void preparer() {
        service = new VerificationService(verificationRepository, pieceRepository, userRepository, chiffrement,
            publisher, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        organisateur = new User("orga@exemple.sn", "hash", "Awa Diop", Role.ORGANISATEUR);
        lenient().when(userRepository.findById(organisateur.getId())).thenReturn(Optional.of(organisateur));
        lenient().when(verificationRepository.save(any(VerificationOrganisateur.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(pieceRepository.resumes(organisateur.getId())).thenReturn(List.of());
        lenient().when(chiffrement.chiffrer(any())).thenReturn(CHIFFRE);
    }

    private VerificationOrganisateur enRevue() {
        VerificationOrganisateur v = VerificationOrganisateur.nouvelle(organisateur.getId());
        v.renseigner(TypeTitulaire.PERSONNE, null, null, "771234567", "Awa Diop", MAINTENANT);
        v.soumettre(Set.of(TypePiece.IDENTITE, TypePiece.JUSTIFICATIF_REVERSEMENT), MAINTENANT);
        return v;
    }

    // ---- typeMime

    @Test
    void reconnait_un_pdf() {
        assertThat(VerificationService.typeMime(PDF)).isEqualTo("application/pdf");
    }

    @Test
    void reconnait_un_jpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};

        assertThat(VerificationService.typeMime(jpeg)).isEqualTo("image/jpeg");
    }

    @Test
    void reconnait_un_png() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

        assertThat(VerificationService.typeMime(png)).isEqualTo("image/png");
    }

    @Test
    void refuse_un_texte() {
        assertThatThrownBy(() -> VerificationService.typeMime("bonjour".getBytes()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_un_fichier_executable_renomme() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 0x03};

        assertThatThrownBy(() -> VerificationService.typeMime(exe)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_une_signature_png_tronquee() {
        byte[] tronque = {(byte) 0x89, 'P', 'N', 'G'};

        assertThatThrownBy(() -> VerificationService.typeMime(tronque)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- deposer

    @Test
    void deposer_refuse_un_fichier_vide_sans_rien_enregistrer() {
        assertThatThrownBy(() -> service.deposer(organisateur.getId(), TypePiece.IDENTITE, new byte[0]))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(chiffrement);
        verify(pieceRepository, never()).remplacer(any());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void deposer_refuse_un_fichier_absent() {
        assertThatThrownBy(() -> service.deposer(organisateur.getId(), TypePiece.IDENTITE, null))
            .isInstanceOf(IllegalArgumentException.class);

        verify(pieceRepository, never()).remplacer(any());
    }

    @Test
    void deposer_refuse_un_fichier_de_plus_de_5_mo_sans_rien_enregistrer() {
        byte[] trop = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, trop, 0, PDF.length);

        assertThatThrownBy(() -> service.deposer(organisateur.getId(), TypePiece.IDENTITE, trop))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(chiffrement);
        verify(pieceRepository, never()).remplacer(any());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void deposer_accepte_un_fichier_de_exactement_5_mo() {
        byte[] limite = new byte[5 * 1024 * 1024];
        System.arraycopy(PDF, 0, limite, 0, PDF.length);

        service.deposer(organisateur.getId(), TypePiece.IDENTITE, limite);

        verify(pieceRepository).remplacer(any(PieceOrganisateur.class));
    }

    @Test
    void deposer_refuse_un_format_non_reconnu_sans_rien_enregistrer() {
        assertThatThrownBy(() -> service.deposer(organisateur.getId(), TypePiece.IDENTITE, "texte".getBytes()))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(chiffrement);
        verify(pieceRepository, never()).remplacer(any());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void deposer_refuse_un_type_de_piece_absent() {
        assertThatThrownBy(() -> service.deposer(organisateur.getId(), null, PDF))
            .isInstanceOf(IllegalArgumentException.class);

        verify(pieceRepository, never()).remplacer(any());
    }

    @Test
    void deposer_chiffre_le_contenu_en_clair_et_remplace_la_piece() {
        service.deposer(organisateur.getId(), TypePiece.IDENTITE, PDF);

        verify(chiffrement).chiffrer(PDF);
        ArgumentCaptor<PieceOrganisateur> piece = ArgumentCaptor.forClass(PieceOrganisateur.class);
        verify(pieceRepository).remplacer(piece.capture());
        assertThat(piece.getValue().getContenu()).isEqualTo(CHIFFRE).isNotEqualTo(PDF);
        assertThat(piece.getValue().getType()).isEqualTo(TypePiece.IDENTITE);
        assertThat(piece.getValue().getTypeMime()).isEqualTo("application/pdf");
        assertThat(piece.getValue().getTaille()).isEqualTo(PDF.length);
        assertThat(piece.getValue().getOrganisateurId()).isEqualTo(organisateur.getId());
        assertThat(piece.getValue().getDeposeLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void deposer_sur_un_compte_verifie_le_repasse_en_revue() {
        VerificationOrganisateur v = enRevue();
        v.verifier(admin, MAINTENANT);
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.of(v));

        service.deposer(organisateur.getId(), TypePiece.IDENTITE, PDF);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.EN_REVUE);
        verify(verificationRepository).save(v);
    }

    @Test
    void deposer_pour_un_agent_leve_une_erreur_et_n_enregistre_rien() {
        User agent = User.agentCreePar(organisateur.getId(), "agent@exemple.sn", "hash", "Agent");
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.deposer(agent.getId(), TypePiece.IDENTITE, PDF))
            .isInstanceOf(NoSuchElementException.class);

        verify(pieceRepository, never()).remplacer(any());
    }

    @Test
    void deposer_pour_un_administrateur_leve_une_erreur() {
        User administrateur = new User("admin@exemple.sn", "hash", "Admin", Role.ADMIN);
        when(userRepository.findById(administrateur.getId())).thenReturn(Optional.of(administrateur));

        assertThatThrownBy(() -> service.deposer(administrateur.getId(), TypePiece.IDENTITE, PDF))
            .isInstanceOf(NoSuchElementException.class);

        verify(pieceRepository, never()).remplacer(any());
    }

    @Test
    void deposer_pour_un_compte_inconnu_leve_une_erreur() {
        UUID inconnu = UUID.randomUUID();
        when(userRepository.findById(inconnu)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposer(inconnu, TypePiece.IDENTITE, PDF))
            .isInstanceOf(NoSuchElementException.class);

        verify(pieceRepository, never()).remplacer(any());
    }

    // ---- verifier / suspendre

    @Test
    void verifier_un_organisateur_sans_verification_leve_une_erreur() {
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifier(organisateur.getId(), admin))
            .isInstanceOf(NoSuchElementException.class);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void verifier_un_compte_en_revue_le_passe_verifie() {
        VerificationOrganisateur v = enRevue();
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.of(v));

        VerificationService.Etat etat = service.verifier(organisateur.getId(), admin);

        assertThat(etat.verification().getStatut()).isEqualTo(StatutVerification.VERIFIE);
        verify(verificationRepository).save(v);
    }

    @Test
    void suspendre_publie_compte_suspendu_et_desactive_le_compte() {
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.empty());

        service.suspendre(organisateur.getId(), admin, "Faux evenement");

        assertThat(organisateur.estActif()).isFalse();
        verify(userRepository).save(organisateur);
        verify(publisher).publishEvent(new CompteSuspendu(organisateur.getId()));
        ArgumentCaptor<VerificationOrganisateur> v = ArgumentCaptor.forClass(VerificationOrganisateur.class);
        verify(verificationRepository).save(v.capture());
        assertThat(v.getValue().getStatut()).isEqualTo(StatutVerification.SUSPENDU);
    }

    @Test
    void suspendre_sans_motif_ne_desactive_rien_et_ne_publie_rien() {
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendre(organisateur.getId(), admin, " "))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(organisateur.estActif()).isTrue();
        verify(userRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void suspendre_un_compte_deja_suspendu_ne_publie_rien() {
        VerificationOrganisateur v = enRevue();
        v.suspendre(admin, "Premier motif", MAINTENANT);
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.suspendre(organisateur.getId(), admin, "Second motif"))
            .isInstanceOf(com.billetplus.shared.exception.BusinessConflictException.class);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void suspendre_un_agent_leve_une_erreur_et_ne_publie_rien() {
        User agent = User.agentCreePar(organisateur.getId(), "agent@exemple.sn", "hash", "Agent");
        when(userRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.suspendre(agent.getId(), admin, "Motif"))
            .isInstanceOf(NoSuchElementException.class);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    // ---- reversement et purge

    @Test
    void reversement_enregistre_met_a_jour_la_date_du_dernier_reversement() {
        VerificationOrganisateur v = enRevue();
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.of(v));

        service.reversementEnregistre(organisateur.getId(), MAINTENANT);

        assertThat(v.getDernierReversementLe()).isEqualTo(MAINTENANT);
        verify(verificationRepository).save(v);
    }

    @Test
    void reversement_enregistre_sans_verification_ne_fait_rien() {
        when(verificationRepository.findById(organisateur.getId())).thenReturn(Optional.empty());

        service.reversementEnregistre(organisateur.getId(), MAINTENANT);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    void purge_les_pieces_echues_depuis_5_ans() {
        when(pieceRepository.supprimerEchues(any())).thenReturn(3);

        int supprimees = service.purgerLesPiecesEchues();

        assertThat(supprimees).isEqualTo(3);
        verify(pieceRepository).supprimerEchues(Instant.parse("2021-11-10T10:00:00Z"));
    }
}
