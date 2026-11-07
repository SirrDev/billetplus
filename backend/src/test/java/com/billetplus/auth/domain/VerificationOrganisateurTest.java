package com.billetplus.auth.domain;

import com.billetplus.auth.domain.VerificationOrganisateur.TypeTitulaire;
import com.billetplus.shared.exception.BusinessConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationOrganisateurTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private static final Set<TypePiece> PIECES_PERSONNE = Set.of(TypePiece.IDENTITE, TypePiece.JUSTIFICATIF_REVERSEMENT);
    private static final Set<TypePiece> TOUTES = Set.of(TypePiece.values());

    private VerificationOrganisateur nouvelle() {
        return VerificationOrganisateur.nouvelle(UUID.randomUUID());
    }

    private VerificationOrganisateur personneDeclaree() {
        VerificationOrganisateur v = nouvelle();
        v.renseigner(TypeTitulaire.PERSONNE, null, null, "77 123 45 67", "Awa Diop", T0);
        return v;
    }

    private VerificationOrganisateur enRevue() {
        VerificationOrganisateur v = personneDeclaree();
        v.soumettre(PIECES_PERSONNE, T0);
        return v;
    }

    private VerificationOrganisateur verifiee() {
        VerificationOrganisateur v = enRevue();
        v.verifier(ADMIN, T0);
        return v;
    }

    // ---- creation

    @Test
    void une_nouvelle_verification_est_a_verifier() {
        VerificationOrganisateur v = nouvelle();

        assertThat(v.getStatut()).isEqualTo(StatutVerification.A_VERIFIER);
        assertThat(v.estVerifie()).isFalse();
    }

    @Test
    void refuse_une_verification_sans_organisateur() {
        assertThatThrownBy(() -> VerificationOrganisateur.nouvelle(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- renseigner

    @Test
    void personne_ignore_le_nom_de_structure_et_le_ninea() {
        VerificationOrganisateur v = nouvelle();

        v.renseigner(TypeTitulaire.PERSONNE, "Ma Boite", "1234567 2G3", "771234567", "Awa Diop", T0);

        assertThat(v.getNomStructure()).isNull();
        assertThat(v.getNinea()).isNull();
        assertThat(v.getTitulaireReversement()).isEqualTo("Awa Diop");
    }

    @Test
    void structure_enregistre_nom_et_ninea() {
        VerificationOrganisateur v = nouvelle();

        v.renseigner(TypeTitulaire.STRUCTURE, " Asso Teranga ", "0012345 2G3", "771234567", "Awa Diop", T0);

        assertThat(v.getNomStructure()).isEqualTo("Asso Teranga");
        assertThat(v.getNinea()).isEqualTo("0012345 2G3");
    }

    @Test
    void structure_sans_nom_est_refusee() {
        VerificationOrganisateur v = nouvelle();

        assertThatThrownBy(() -> v.renseigner(TypeTitulaire.STRUCTURE, "  ", "0012345 2G3", "771234567", "Awa Diop", T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structure_sans_ninea_est_refusee() {
        VerificationOrganisateur v = nouvelle();

        assertThatThrownBy(() -> v.renseigner(TypeTitulaire.STRUCTURE, "Asso", null, "771234567", "Awa Diop", T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structure_avec_ninea_trop_court_est_refusee() {
        VerificationOrganisateur v = nouvelle();

        assertThatThrownBy(() -> v.renseigner(TypeTitulaire.STRUCTURE, "Asso", "1234", "771234567", "Awa Diop", T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_un_type_de_titulaire_absent() {
        VerificationOrganisateur v = nouvelle();

        assertThatThrownBy(() -> v.renseigner(null, null, null, "771234567", "Awa Diop", T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void titulaire_obligatoire() {
        VerificationOrganisateur v = nouvelle();

        assertThatThrownBy(() -> v.renseigner(TypeTitulaire.PERSONNE, null, null, "771234567", "   ", T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void titulaire_de_plus_de_255_caracteres_refuse() {
        VerificationOrganisateur v = nouvelle();
        String trop = "a".repeat(256);

        assertThatThrownBy(() -> v.renseigner(TypeTitulaire.PERSONNE, null, null, "771234567", trop, T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numero_avec_espaces_est_normalise() {
        assertThat(VerificationOrganisateur.normaliserNumero("77 123 45 67")).isEqualTo("+221771234567");
    }

    @Test
    void numero_avec_indicatif_plus_221_est_normalise() {
        assertThat(VerificationOrganisateur.normaliserNumero("+221 77 123 45 67")).isEqualTo("+221771234567");
    }

    @Test
    void numero_avec_indicatif_00221_est_normalise() {
        assertThat(VerificationOrganisateur.normaliserNumero("00221771234567")).isEqualTo("+221771234567");
    }

    @Test
    void numero_avec_indicatif_221_sans_plus_est_normalise() {
        assertThat(VerificationOrganisateur.normaliserNumero("221771234567")).isEqualTo("+221771234567");
    }

    @Test
    void numero_fixe_est_refuse() {
        assertThatThrownBy(() -> VerificationOrganisateur.normaliserNumero("33 123 45 67"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numero_de_8_chiffres_est_refuse() {
        assertThatThrownBy(() -> VerificationOrganisateur.normaliserNumero("77123456"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numero_absent_est_refuse() {
        assertThatThrownBy(() -> VerificationOrganisateur.normaliserNumero(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numero_avec_lettres_est_refuse() {
        assertThatThrownBy(() -> VerificationOrganisateur.normaliserNumero("77abc4567"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renseigner_enregistre_le_numero_normalise() {
        VerificationOrganisateur v = personneDeclaree();

        assertThat(v.getNumeroReversement()).isEqualTo("+221771234567");
    }

    @Test
    void renseigner_avec_un_numero_invalide_ne_modifie_rien() {
        VerificationOrganisateur v = personneDeclaree();

        assertThatThrownBy(() -> v.renseigner(TypeTitulaire.PERSONNE, null, null, "331234567", "Autre Nom", T1))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(v.getNumeroReversement()).isEqualTo("+221771234567");
        assertThat(v.getTitulaireReversement()).isEqualTo("Awa Diop");
    }

    // ---- compte verifie

    @Test
    void changement_de_numero_sur_un_compte_verifie_repasse_en_revue() {
        VerificationOrganisateur v = verifiee();

        v.renseigner(TypeTitulaire.PERSONNE, null, null, "78 123 45 67", "Awa Diop", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.EN_REVUE);
        assertThat(v.estVerifie()).isFalse();
        assertThat(v.getSoumisLe()).isEqualTo(T1);
        assertThat(v.getDecideLe()).isNull();
        assertThat(v.getDecidePar()).isNull();
    }

    @Test
    void changement_de_titulaire_sur_un_compte_verifie_repasse_en_revue() {
        VerificationOrganisateur v = verifiee();

        v.renseigner(TypeTitulaire.PERSONNE, null, null, "77 123 45 67", "Moussa Fall", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.EN_REVUE);
    }

    @Test
    void declaration_identique_sur_un_compte_verifie_reste_verifie() {
        VerificationOrganisateur v = verifiee();

        v.renseigner(TypeTitulaire.PERSONNE, null, null, "+221 77 123 45 67", "Awa Diop", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.VERIFIE);
        assertThat(v.estVerifie()).isTrue();
    }

    @Test
    void piece_deposee_sur_un_compte_verifie_repasse_en_revue() {
        VerificationOrganisateur v = verifiee();

        v.pieceDeposee(T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.EN_REVUE);
    }

    @Test
    void piece_deposee_sur_un_compte_a_verifier_ne_change_pas_le_statut() {
        VerificationOrganisateur v = nouvelle();

        v.pieceDeposee(T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.A_VERIFIER);
    }

    // ---- pieces exigees

    @Test
    void personne_doit_fournir_deux_pieces() {
        assertThat(personneDeclaree().piecesExigees())
            .containsExactlyInAnyOrder(TypePiece.IDENTITE, TypePiece.JUSTIFICATIF_REVERSEMENT);
    }

    @Test
    void structure_doit_fournir_trois_pieces() {
        VerificationOrganisateur v = nouvelle();
        v.renseigner(TypeTitulaire.STRUCTURE, "Asso", "0012345 2G3", "771234567", "Awa Diop", T0);

        assertThat(v.piecesExigees()).hasSize(3).contains(TypePiece.NINEA_RCCM);
    }

    // ---- soumettre

    @Test
    void soumettre_passe_en_revue_avec_la_date() {
        VerificationOrganisateur v = personneDeclaree();

        v.soumettre(PIECES_PERSONNE, T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.EN_REVUE);
        assertThat(v.getSoumisLe()).isEqualTo(T1);
    }

    @Test
    void soumettre_sans_declaration_est_refuse() {
        VerificationOrganisateur v = nouvelle();

        assertThatThrownBy(() -> v.soumettre(TOUTES, T1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(v.getStatut()).isEqualTo(StatutVerification.A_VERIFIER);
    }

    @Test
    void soumettre_avec_une_piece_manquante_est_refuse() {
        VerificationOrganisateur v = personneDeclaree();

        assertThatThrownBy(() -> v.soumettre(Set.of(TypePiece.IDENTITE), T1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(v.getStatut()).isEqualTo(StatutVerification.A_VERIFIER);
    }

    @Test
    void structure_sans_ninea_rccm_depose_ne_peut_pas_soumettre() {
        VerificationOrganisateur v = nouvelle();
        v.renseigner(TypeTitulaire.STRUCTURE, "Asso", "0012345 2G3", "771234567", "Awa Diop", T0);

        assertThatThrownBy(() -> v.soumettre(PIECES_PERSONNE, T1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soumettre_depuis_en_revue_est_un_conflit() {
        VerificationOrganisateur v = enRevue();

        assertThatThrownBy(() -> v.soumettre(PIECES_PERSONNE, T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void soumettre_depuis_verifie_est_un_conflit() {
        VerificationOrganisateur v = verifiee();

        assertThatThrownBy(() -> v.soumettre(PIECES_PERSONNE, T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void soumettre_depuis_suspendu_est_un_conflit() {
        VerificationOrganisateur v = enRevue();
        v.suspendre(ADMIN, "Fraude", T1);

        assertThatThrownBy(() -> v.soumettre(PIECES_PERSONNE, T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void soumettre_depuis_refuse_repasse_en_revue_et_efface_le_motif() {
        VerificationOrganisateur v = enRevue();
        v.refuser(ADMIN, "Piece illisible", T0);
        assertThat(v.getMotif()).isEqualTo("Piece illisible");

        v.soumettre(PIECES_PERSONNE, T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.EN_REVUE);
        assertThat(v.getMotif()).isNull();
        assertThat(v.getDecideLe()).isNull();
        assertThat(v.getDecidePar()).isNull();
    }

    // ---- verifier / refuser

    @Test
    void verifier_depuis_en_revue_enregistre_la_decision() {
        VerificationOrganisateur v = enRevue();

        v.verifier(ADMIN, T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.VERIFIE);
        assertThat(v.estVerifie()).isTrue();
        assertThat(v.getDecidePar()).isEqualTo(ADMIN);
        assertThat(v.getDecideLe()).isEqualTo(T1);
    }

    @Test
    void verifier_depuis_a_verifier_est_un_conflit() {
        VerificationOrganisateur v = personneDeclaree();

        assertThatThrownBy(() -> v.verifier(ADMIN, T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void verifier_depuis_verifie_est_un_conflit() {
        VerificationOrganisateur v = verifiee();

        assertThatThrownBy(() -> v.verifier(ADMIN, T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void verifier_depuis_refuse_est_un_conflit() {
        VerificationOrganisateur v = enRevue();
        v.refuser(ADMIN, "Non conforme", T0);

        assertThatThrownBy(() -> v.verifier(ADMIN, T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void verifier_depuis_suspendu_est_un_conflit() {
        VerificationOrganisateur v = enRevue();
        v.suspendre(ADMIN, "Fraude", T0);

        assertThatThrownBy(() -> v.verifier(ADMIN, T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void verifier_sans_administrateur_est_refuse() {
        VerificationOrganisateur v = enRevue();

        assertThatThrownBy(() -> v.verifier(null, T1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuser_depuis_en_revue_enregistre_le_motif() {
        VerificationOrganisateur v = enRevue();

        v.refuser(ADMIN, "  CNI illisible  ", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.REFUSE);
        assertThat(v.getMotif()).isEqualTo("CNI illisible");
        assertThat(v.getDecidePar()).isEqualTo(ADMIN);
    }

    @Test
    void refuser_sans_motif_est_refuse() {
        VerificationOrganisateur v = enRevue();

        assertThatThrownBy(() -> v.refuser(ADMIN, " ", T1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(v.getStatut()).isEqualTo(StatutVerification.EN_REVUE);
    }

    @Test
    void refuser_avec_un_motif_de_501_caracteres_est_refuse() {
        VerificationOrganisateur v = enRevue();
        String trop = "m".repeat(VerificationOrganisateur.MOTIF_MAX + 1);

        assertThatThrownBy(() -> v.refuser(ADMIN, trop, T1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuser_depuis_a_verifier_est_un_conflit() {
        VerificationOrganisateur v = personneDeclaree();

        assertThatThrownBy(() -> v.refuser(ADMIN, "Motif", T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void refuser_depuis_verifie_est_un_conflit() {
        VerificationOrganisateur v = verifiee();

        assertThatThrownBy(() -> v.refuser(ADMIN, "Motif", T1)).isInstanceOf(BusinessConflictException.class);
        assertThat(v.getStatut()).isEqualTo(StatutVerification.VERIFIE);
    }

    // ---- suspendre

    @Test
    void suspendre_depuis_a_verifier() {
        VerificationOrganisateur v = nouvelle();

        v.suspendre(ADMIN, "Fraude averee", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.SUSPENDU);
        assertThat(v.getMotif()).isEqualTo("Fraude averee");
        assertThat(v.getDecidePar()).isEqualTo(ADMIN);
    }

    @Test
    void suspendre_depuis_en_revue() {
        VerificationOrganisateur v = enRevue();

        v.suspendre(ADMIN, "Fraude averee", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.SUSPENDU);
    }

    @Test
    void suspendre_depuis_verifie() {
        VerificationOrganisateur v = verifiee();

        v.suspendre(ADMIN, "Fraude averee", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.SUSPENDU);
        assertThat(v.estVerifie()).isFalse();
    }

    @Test
    void suspendre_depuis_refuse() {
        VerificationOrganisateur v = enRevue();
        v.refuser(ADMIN, "Non conforme", T0);

        v.suspendre(ADMIN, "Fraude averee", T1);

        assertThat(v.getStatut()).isEqualTo(StatutVerification.SUSPENDU);
        assertThat(v.getMotif()).isEqualTo("Fraude averee");
    }

    @Test
    void suspendre_deux_fois_est_un_conflit() {
        VerificationOrganisateur v = enRevue();
        v.suspendre(ADMIN, "Fraude", T0);

        assertThatThrownBy(() -> v.suspendre(ADMIN, "Fraude", T1)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void suspendre_sans_motif_est_refuse() {
        VerificationOrganisateur v = verifiee();

        assertThatThrownBy(() -> v.suspendre(ADMIN, null, T1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(v.getStatut()).isEqualTo(StatutVerification.VERIFIE);
    }

    @Test
    void renseigner_sur_un_compte_suspendu_est_un_conflit() {
        VerificationOrganisateur v = verifiee();
        v.suspendre(ADMIN, "Fraude", T0);

        assertThatThrownBy(() -> v.renseigner(TypeTitulaire.PERSONNE, null, null, "771234567", "Awa Diop", T1))
            .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void piece_deposee_sur_un_compte_suspendu_est_un_conflit() {
        VerificationOrganisateur v = verifiee();
        v.suspendre(ADMIN, "Fraude", T0);

        assertThatThrownBy(() -> v.pieceDeposee(T1)).isInstanceOf(BusinessConflictException.class);
        assertThat(v.getStatut()).isEqualTo(StatutVerification.SUSPENDU);
    }

    // ---- dernier reversement

    @Test
    void premier_reversement_est_enregistre() {
        VerificationOrganisateur v = verifiee();

        v.reversementEnregistre(T0);

        assertThat(v.getDernierReversementLe()).isEqualTo(T0);
    }

    @Test
    void reversement_plus_recent_remplace_la_date() {
        VerificationOrganisateur v = verifiee();
        v.reversementEnregistre(T0);

        v.reversementEnregistre(T1);

        assertThat(v.getDernierReversementLe()).isEqualTo(T1);
    }

    @Test
    void reversement_plus_ancien_ne_recule_pas_la_date() {
        VerificationOrganisateur v = verifiee();
        v.reversementEnregistre(T1);

        v.reversementEnregistre(T0);

        assertThat(v.getDernierReversementLe()).isEqualTo(T1);
    }

    @Test
    void reversement_sans_date_est_ignore() {
        VerificationOrganisateur v = verifiee();
        v.reversementEnregistre(T0);

        v.reversementEnregistre(null);

        assertThat(v.getDernierReversementLe()).isEqualTo(T0);
    }
}
