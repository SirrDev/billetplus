package com.billetplus.auth.api.dto;

import com.billetplus.auth.application.VerificationService;
import com.billetplus.auth.domain.VerificationOrganisateur;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vérification d'un organisateur (ADR 0012), sans le contenu des pièces. Même réponse pour
 * l'organisateur (sur son propre compte) et pour l'administrateur ; seul ce dernier lit les
 * fichiers, par une route dédiée.
 */
public record VerificationDto(
    UUID organisateurId,
    String nomComplet,
    String email,
    String statut,
    String typeTitulaire,
    String nomStructure,
    String ninea,
    String numeroReversement,
    String titulaireReversement,
    String motif,
    Instant soumisLe,
    Instant decideLe,
    List<String> piecesExigees,
    List<Piece> pieces
) {
    public record Piece(String type, String typeMime, int taille, Instant deposeLe) {
    }

    public static VerificationDto from(VerificationService.Etat etat) {
        VerificationOrganisateur v = etat.verification();
        return new VerificationDto(
            v.getOrganisateurId(),
            etat.compte() == null ? null : etat.compte().getNomComplet(),
            etat.compte() == null ? null : etat.compte().getEmail(),
            v.getStatut().name(),
            v.getTypeTitulaire() == null ? null : v.getTypeTitulaire().name(),
            v.getNomStructure(), v.getNinea(), v.getNumeroReversement(), v.getTitulaireReversement(),
            v.getMotif(), v.getSoumisLe(), v.getDecideLe(),
            v.piecesExigees().stream().map(Enum::name).sorted().toList(),
            etat.pieces().stream().map(p -> new Piece(p.type().name(), p.typeMime(), p.taille(), p.deposeLe())).toList()
        );
    }
}
