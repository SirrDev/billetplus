package com.billetplus.auth.infrastructure;

import com.billetplus.auth.domain.PieceOrganisateur;
import com.billetplus.auth.domain.TypePiece;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Interface Spring Data — usage interne à infrastructure/ uniquement. */
interface SpringDataPieceRepository extends JpaRepository<PieceOrganisateur, UUID> {

    /** Projection sans le contenu chiffré : une liste ne lit jamais les fichiers. */
    interface Resume {
        TypePiece getType();

        String getTypeMime();

        int getTaille();

        Instant getDeposeLe();
    }

    List<Resume> findByOrganisateurIdOrderByTypeAsc(UUID organisateurId);

    Optional<PieceOrganisateur> findByOrganisateurIdAndType(UUID organisateurId, TypePiece type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PieceOrganisateur p where p.organisateurId = :organisateur and p.type = :type")
    int supprimer(@Param("organisateur") UUID organisateurId, @Param("type") TypePiece type);

    /**
     * 5 ans après le dernier reversement, ou après le dépôt sans reversement (ADR 0012). La date
     * la plus récente des deux compte : une pièce déposée aujourd'hui n'est jamais purgée à cause
     * d'un vieux reversement (greatest ignore les NULL en PostgreSQL).
     */
    @Modifying
    @Query(value = "DELETE FROM pieces_organisateurs p USING verifications_organisateurs v "
        + "WHERE p.organisateur_id = v.organisateur_id "
        + "AND greatest(v.dernier_reversement_le, p.depose_le) < :limite", nativeQuery = true)
    int supprimerEchues(@Param("limite") Instant limite);
}
