package com.billetplus.auth.application;

import com.billetplus.auth.domain.PieceOrganisateur;
import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.StatutVerification;
import com.billetplus.auth.domain.TypePiece;
import com.billetplus.auth.domain.User;
import com.billetplus.auth.domain.VerificationOrganisateur;
import com.billetplus.shared.evenements.CompteSuspendu;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Vérification des organisateurs (ADR 0012) : déclaration du titulaire, dépôt des pièces
 * chiffrées, demande d'examen, décision et suspension par l'administrateur.
 * L'organisateur et l'administrateur viennent toujours du jeton.
 */
@Service
public class VerificationService {

    /** Ce que voit l'organisateur (ou l'administrateur) d'une vérification. */
    public record Etat(VerificationOrganisateur verification, List<PieceRepository.Resume> pieces, User compte) {
    }

    /** Une pièce déchiffrée, pour l'administrateur seulement. */
    public record PieceEnClair(String typeMime, byte[] contenu) {
    }

    private final VerificationRepository verificationRepository;
    private final PieceRepository pieceRepository;
    private final UserRepository userRepository;
    private final ChiffrementPieces chiffrement;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public VerificationService(VerificationRepository verificationRepository, PieceRepository pieceRepository,
                               UserRepository userRepository, ChiffrementPieces chiffrement,
                               ApplicationEventPublisher publisher, Clock clock) {
        this.verificationRepository = verificationRepository;
        this.pieceRepository = pieceRepository;
        this.userRepository = userRepository;
        this.chiffrement = chiffrement;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- organisateur

    @Transactional(readOnly = true)
    public Etat consulter(UUID organisateurId) {
        User compte = organisateur(organisateurId);
        VerificationOrganisateur v = verificationRepository.findById(organisateurId)
            .orElseGet(() -> VerificationOrganisateur.nouvelle(organisateurId));
        return new Etat(v, pieceRepository.resumes(organisateurId), compte);
    }

    /** @throws IllegalArgumentException (400) déclaration invalide ; BusinessConflictException (409) suspendu */
    @Transactional
    public Etat renseigner(UUID organisateurId, VerificationOrganisateur.TypeTitulaire type, String nomStructure,
                           String ninea, String numero, String titulaire) {
        VerificationOrganisateur v = existanteOuNouvelle(organisateurId);
        v.renseigner(type, nomStructure, ninea, numero, titulaire, Instant.now(clock));
        verificationRepository.save(v);
        return consulter(organisateurId);
    }

    /**
     * Dépose (ou remplace) une pièce. Le format est lu dans le contenu (signature du fichier),
     * jamais dans le nom ni dans l'en-tête Content-Type fourni par le client.
     *
     * @throws IllegalArgumentException (400) fichier vide, trop lourd ou d'un format refusé
     */
    @Transactional
    public Etat deposer(UUID organisateurId, TypePiece type, byte[] contenu) {
        if (type == null) {
            throw new IllegalArgumentException("Type de pièce inconnu");
        }
        if (contenu == null || contenu.length == 0) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        if (contenu.length > PieceOrganisateur.TAILLE_MAX) {
            throw new IllegalArgumentException("Le fichier doit faire 5 Mo au plus");
        }
        String typeMime = typeMime(contenu);
        VerificationOrganisateur v = existanteOuNouvelle(organisateurId);
        Instant maintenant = Instant.now(clock);
        v.pieceDeposee(maintenant);
        verificationRepository.save(v);
        pieceRepository.remplacer(new PieceOrganisateur(organisateurId, type, typeMime, contenu.length,
            chiffrement.chiffrer(contenu), maintenant));
        return consulter(organisateurId);
    }

    /** @throws IllegalArgumentException (400) pièces ou déclaration manquantes ; BusinessConflictException (409) */
    @Transactional
    public Etat soumettre(UUID organisateurId) {
        VerificationOrganisateur v = existanteOuNouvelle(organisateurId);
        Set<TypePiece> deposees = new HashSet<>();
        pieceRepository.resumes(organisateurId).forEach(p -> deposees.add(p.type()));
        v.soumettre(deposees, Instant.now(clock));
        verificationRepository.save(v);
        return consulter(organisateurId);
    }

    // ---------------------------------------------------------------- administrateur

    @Transactional(readOnly = true)
    public List<Etat> parStatut(StatutVerification statut) {
        return verificationRepository.findByStatut(statut).stream()
            .map(v -> new Etat(v, pieceRepository.resumes(v.getOrganisateurId()),
                userRepository.findById(v.getOrganisateurId()).orElse(null)))
            .filter(e -> e.compte() != null)
            .toList();
    }

    /** @throws NoSuchElementException (404) organisateur ou pièce inconnus */
    @Transactional(readOnly = true)
    public PieceEnClair lirePiece(UUID organisateurId, TypePiece type) {
        organisateur(organisateurId);
        PieceOrganisateur piece = pieceRepository.trouver(organisateurId, type)
            .orElseThrow(() -> new NoSuchElementException("Pièce introuvable"));
        return new PieceEnClair(piece.getTypeMime(), chiffrement.dechiffrer(piece.getContenu()));
    }

    @Transactional
    public Etat verifier(UUID organisateurId, UUID administrateurId) {
        VerificationOrganisateur v = existante(organisateurId);
        v.verifier(administrateurId, Instant.now(clock));
        verificationRepository.save(v);
        return consulter(organisateurId);
    }

    @Transactional
    public Etat refuser(UUID organisateurId, UUID administrateurId, String motif) {
        VerificationOrganisateur v = existante(organisateurId);
        v.refuser(administrateurId, motif, Instant.now(clock));
        verificationRepository.save(v);
        return consulter(organisateurId);
    }

    /**
     * Suspension définitive (ADR 0012, point 6) : compte désactivé (connexion refusée) et
     * {@link CompteSuspendu} publié dans la même transaction (le module events annule les
     * événements à venir ; s'il échoue, rien n'est suspendu).
     */
    @Transactional
    public Etat suspendre(UUID organisateurId, UUID administrateurId, String motif) {
        User compte = organisateur(organisateurId);
        VerificationOrganisateur v = existanteOuNouvelle(organisateurId);
        Instant maintenant = Instant.now(clock);
        v.suspendre(administrateurId, motif, maintenant);
        verificationRepository.save(v);
        compte.suspendre(maintenant);
        userRepository.save(compte);
        // Ses agents aussi : ils ne contrôlent plus rien pour lui (ADR 0012).
        for (User agent : userRepository.findByCreePar(organisateurId)) {
            if (agent.getRole() == Role.AGENT && agent.estActif()) {
                agent.desactiver();
                userRepository.save(agent);
            }
        }
        publisher.publishEvent(new CompteSuspendu(organisateurId));
        return consulter(organisateurId);
    }

    // ---------------------------------------------------------------- autres modules

    /** Dernier reversement : point de départ de la conservation des pièces. */
    @Transactional
    public void reversementEnregistre(UUID organisateurId, Instant le) {
        verificationRepository.findById(organisateurId).ifPresent(v -> {
            v.reversementEnregistre(le);
            verificationRepository.save(v);
        });
    }

    /** Suppression des pièces échues (tâche quotidienne). */
    @Transactional
    public int purgerLesPiecesEchues() {
        return pieceRepository.supprimerEchues(Instant.now(clock).atZone(java.time.ZoneOffset.UTC).minusYears(5)
            .toInstant());
    }

    // ---------------------------------------------------------------- outils

    private VerificationOrganisateur existanteOuNouvelle(UUID organisateurId) {
        organisateur(organisateurId);
        return verificationRepository.findById(organisateurId)
            .orElseGet(() -> VerificationOrganisateur.nouvelle(organisateurId));
    }

    private VerificationOrganisateur existante(UUID organisateurId) {
        organisateur(organisateurId);
        return verificationRepository.findById(organisateurId)
            .orElseThrow(() -> new NoSuchElementException("Aucune demande de vérification pour ce compte"));
    }

    /** @throws NoSuchElementException (404) si ce n'est pas un compte organisateur */
    private User organisateur(UUID id) {
        return userRepository.findById(id)
            .filter(u -> u.getRole() == Role.ORGANISATEUR)
            .orElseThrow(() -> new NoSuchElementException("Organisateur introuvable"));
    }

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** Format lu dans les premiers octets du fichier. */
    static String typeMime(byte[] contenu) {
        if (commencePar(contenu, PDF)) {
            return "application/pdf";
        }
        if (commencePar(contenu, JPEG)) {
            return "image/jpeg";
        }
        if (commencePar(contenu, PNG)) {
            return "image/png";
        }
        throw new IllegalArgumentException("Format refusé : PDF, JPEG ou PNG seulement");
    }

    private static boolean commencePar(byte[] contenu, byte[] signature) {
        return contenu.length >= signature.length
            && Arrays.equals(Arrays.copyOf(contenu, signature.length), signature);
    }
}
