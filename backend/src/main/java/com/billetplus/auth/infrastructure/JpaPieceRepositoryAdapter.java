package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.PieceRepository;
import com.billetplus.auth.domain.PieceOrganisateur;
import com.billetplus.auth.domain.TypePiece;
import com.billetplus.shared.exception.BusinessConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptateur du port PieceRepository (ADR 0012). Le contenu y arrive déjà chiffré. */
@Component
class JpaPieceRepositoryAdapter implements PieceRepository {

    private final SpringDataPieceRepository springRepo;

    JpaPieceRepositoryAdapter(SpringDataPieceRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void remplacer(PieceOrganisateur piece) {
        // Suppression écrite avant l'insertion : la contrainte uq_pieces_organisateur_type
        // ne voit jamais deux pièces du même type.
        springRepo.supprimer(piece.getOrganisateurId(), piece.getType());
        try {
            springRepo.saveAndFlush(piece);
        } catch (DataIntegrityViolationException e) {
            // Deux dépôts simultanés du même type : le second voit la pièce de l'autre au flush.
            if (Contraintes.violee(e, "uq_pieces_organisateur_type")) {
                throw new BusinessConflictException("Une autre pièce de ce type vient d'être déposée : rechargez la page");
            }
            throw e;
        }
    }

    @Override
    public List<Resume> resumes(UUID organisateurId) {
        return springRepo.findByOrganisateurIdOrderByTypeAsc(organisateurId).stream()
            .map(r -> new Resume(r.getType(), r.getTypeMime(), r.getTaille(), r.getDeposeLe()))
            .toList();
    }

    @Override
    public Optional<PieceOrganisateur> trouver(UUID organisateurId, TypePiece type) {
        return springRepo.findByOrganisateurIdAndType(organisateurId, type);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int supprimerEchues(Instant limite) {
        return springRepo.supprimerEchues(limite);
    }
}
