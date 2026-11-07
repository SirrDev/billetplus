package com.billetplus.auth.api;

import com.billetplus.auth.api.dto.DecisionRequest;
import com.billetplus.auth.api.dto.DeclarationVerificationRequest;
import com.billetplus.auth.api.dto.VerificationDto;
import com.billetplus.auth.application.VerificationService;
import com.billetplus.auth.domain.StatutVerification;
import com.billetplus.auth.domain.TypePiece;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Vérification des organisateurs (ADR 0012).
 * - /api/verification : l'organisateur, sur SON compte (identifiant lu dans le jeton) ;
 * - /api/admin/verifications : l'administrateur. Toutes les réponses sont {@code no-store}.
 */
@RestController
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    // ---------------------------------------------------------------- organisateur

    @GetMapping("/api/verification")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<VerificationDto> consulter(Authentication auth) {
        return sansCache(verificationService.consulter(moi(auth)));
    }

    @PutMapping("/api/verification")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<VerificationDto> renseigner(@Valid @RequestBody DeclarationVerificationRequest r,
                                                      Authentication auth) {
        return sansCache(verificationService.renseigner(moi(auth), r.typeTitulaire(), r.nomStructure(), r.ninea(),
            r.numeroReversement(), r.titulaireReversement()));
    }

    /** Dépôt d'une pièce (multipart, champ « fichier ») ; remplace la précédente du même type. */
    @PostMapping(value = "/api/verification/pieces/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<VerificationDto> deposer(@PathVariable String type, @RequestParam("fichier") MultipartFile fichier,
                                                   Authentication auth) throws IOException {
        return sansCache(verificationService.deposer(moi(auth), typePiece(type), fichier.getBytes()));
    }

    @PostMapping("/api/verification/soumission")
    @PreAuthorize("hasRole('ORGANISATEUR')")
    public ResponseEntity<VerificationDto> soumettre(Authentication auth) {
        return sansCache(verificationService.soumettre(moi(auth)));
    }

    // ---------------------------------------------------------------- administrateur

    @GetMapping("/api/admin/verifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VerificationDto>> parStatut(@RequestParam(defaultValue = "EN_REVUE") String statut) {
        StatutVerification s = Arrays.stream(StatutVerification.values()).filter(v -> v.name().equals(statut))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Statut inconnu"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(verificationService.parStatut(s).stream().map(VerificationDto::from).toList());
    }

    @GetMapping("/api/admin/verifications/{organisateurId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationDto> detail(@PathVariable UUID organisateurId) {
        return sansCache(verificationService.consulter(organisateurId));
    }

    /** Pièce déchiffrée, en téléchargement (jamais affichée dans la page, jamais mise en cache). */
    @GetMapping("/api/admin/verifications/{organisateurId}/pieces/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> piece(@PathVariable UUID organisateurId, @PathVariable String type) {
        TypePiece t = typePiece(type);
        VerificationService.PieceEnClair piece = verificationService.lirePiece(organisateurId, t);
        String extension = switch (piece.typeMime()) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            default -> "jpg";
        };
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(piece.typeMime()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(t.name().toLowerCase() + "." + extension).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(piece.contenu());
    }

    @PostMapping("/api/admin/verifications/{organisateurId}/acceptation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationDto> accepter(@PathVariable UUID organisateurId, Authentication auth) {
        return sansCache(verificationService.verifier(organisateurId, moi(auth)));
    }

    @PostMapping("/api/admin/verifications/{organisateurId}/refus")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationDto> refuser(@PathVariable UUID organisateurId,
                                                   @Valid @RequestBody DecisionRequest r, Authentication auth) {
        return sansCache(verificationService.refuser(organisateurId, moi(auth), r.motif()));
    }

    @PostMapping("/api/admin/verifications/{organisateurId}/suspension")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationDto> suspendre(@PathVariable UUID organisateurId,
                                                     @Valid @RequestBody DecisionRequest r, Authentication auth) {
        return sansCache(verificationService.suspendre(organisateurId, moi(auth), r.motif()));
    }

    // ---------------------------------------------------------------- outils

    private static UUID moi(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }

    private static TypePiece typePiece(String type) {
        return Arrays.stream(TypePiece.values()).filter(t -> t.name().equals(type)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Type de pièce inconnu"));
    }

    private static ResponseEntity<VerificationDto> sansCache(VerificationService.Etat etat) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(VerificationDto.from(etat));
    }
}
