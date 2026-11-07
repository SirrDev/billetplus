package com.billetplus.auth.application;

import com.billetplus.auth.domain.PieceOrganisateur;
import com.billetplus.auth.domain.TypePiece;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port (niveau 2) — pièces chiffrées des organisateurs (ADR 0012). */
public interface PieceRepository {

    /** Ce qu'on montre d'une pièce sans lire son contenu chiffré. */
    record Resume(TypePiece type, String typeMime, int taille, Instant deposeLe) {
    }

    /** Remplace la pièce de ce type, s'il y en avait une. Exige une transaction ouverte. */
    void remplacer(PieceOrganisateur piece);

    /** Pièces de l'organisateur, sans leur contenu. */
    List<Resume> resumes(UUID organisateurId);

    /** La pièce complète (contenu chiffré), pour l'administrateur. */
    Optional<PieceOrganisateur> trouver(UUID organisateurId, TypePiece type);

    /**
     * Supprime les pièces échues : 5 ans après la plus récente de ces deux dates, le dernier
     * reversement de l'organisateur et le dépôt de la pièce (ADR 0012).
     *
     * @return nombre de pièces supprimées
     */
    int supprimerEchues(Instant limite);
}
