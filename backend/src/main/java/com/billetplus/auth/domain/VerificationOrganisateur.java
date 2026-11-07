package com.billetplus.auth.domain;

import com.billetplus.shared.exception.BusinessConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Module auth — niveau 2 (JPA-as-domain). Vérification d'un compte organisateur (ADR 0012) :
 * statut, titulaire et numéro de reversement déclarés, décision de l'administrateur.
 * Les transitions interdites lèvent une BusinessConflictException (409).
 */
@Entity
@Table(name = "verifications_organisateurs")
public class VerificationOrganisateur {

    public enum TypeTitulaire {
        /** Personne physique : pièce d'identité et justificatif du numéro. */
        PERSONNE,
        /** Association ou entreprise : en plus, NINEA ou registre du commerce. */
        STRUCTURE
    }

    public static final int MOTIF_MAX = 500;
    private static final Pattern NUMERO = Pattern.compile("^\\+2217[0-9]{8}$");
    private static final Pattern NINEA = Pattern.compile("^[A-Za-z0-9 ./-]{5,30}$");

    @Id
    private UUID organisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutVerification statut;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TypeTitulaire typeTitulaire;

    @Column(length = 255)
    private String nomStructure;

    @Column(length = 30)
    private String ninea;

    @Column(length = 13)
    private String numeroReversement;

    @Column(length = 255)
    private String titulaireReversement;

    private Instant soumisLe;
    private Instant decideLe;
    private UUID decidePar;

    @Column(length = MOTIF_MAX)
    private String motif;

    private Instant dernierReversementLe;

    @Version
    @Column(nullable = false)
    private Long version;

    protected VerificationOrganisateur() {
        // requis par JPA
    }

    /** Premier contact d'un organisateur avec la vérification : rien de déclaré. */
    public static VerificationOrganisateur nouvelle(UUID organisateurId) {
        if (organisateurId == null) {
            throw new IllegalArgumentException("L'organisateur est obligatoire");
        }
        VerificationOrganisateur v = new VerificationOrganisateur();
        v.organisateurId = organisateurId;
        v.statut = StatutVerification.A_VERIFIER;
        return v;
    }

    /**
     * Titulaire et numéro de reversement déclarés. Sur un compte vérifié, tout changement le
     * repasse EN_REVUE : l'administrateur doit revoir le nouveau numéro avant de reverser.
     *
     * @param numeroSaisi mobile sénégalais (77 123 45 67, +221 77 123 45 67...)
     * @throws IllegalArgumentException  (400) déclaration incomplète ou invalide
     * @throws BusinessConflictException (409) compte suspendu, ou demande en cours d'examen
     */
    public void renseigner(TypeTitulaire type, String nomStructure, String ninea, String numeroSaisi,
                           String titulaire, Instant maintenant) {
        exigerModifiable();
        if (type == null) {
            throw new IllegalArgumentException("Indiquez s'il s'agit d'une personne ou d'une structure");
        }
        String nom = nettoyer(nomStructure);
        String identifiant = nettoyer(ninea);
        if (type == TypeTitulaire.STRUCTURE) {
            if (nom == null || nom.length() > 255) {
                throw new IllegalArgumentException("Le nom de la structure est obligatoire (255 caractères au plus)");
            }
            if (identifiant == null || !NINEA.matcher(identifiant).matches()) {
                throw new IllegalArgumentException("Le NINEA ou le numéro RCCM est obligatoire pour une structure");
            }
        } else {
            nom = null;
            identifiant = null;
        }
        String numero = normaliserNumero(numeroSaisi);
        String nomTitulaire = nettoyer(titulaire);
        if (nomTitulaire == null || nomTitulaire.length() > 255) {
            throw new IllegalArgumentException("Le nom du titulaire du numéro est obligatoire (255 caractères au plus)");
        }
        boolean change = type != typeTitulaire || !java.util.Objects.equals(nom, this.nomStructure)
            || !java.util.Objects.equals(identifiant, this.ninea) || !numero.equals(numeroReversement)
            || !nomTitulaire.equals(titulaireReversement);
        this.typeTitulaire = type;
        this.nomStructure = nom;
        this.ninea = identifiant;
        this.numeroReversement = numero;
        this.titulaireReversement = nomTitulaire;
        if (change && statut == StatutVerification.VERIFIE) {
            repasserEnRevue(maintenant);
        }
    }

    /**
     * Une pièce vient d'être déposée ou remplacée. Sur un compte vérifié, retour EN_REVUE.
     *
     * @throws BusinessConflictException (409) compte suspendu
     */
    public void pieceDeposee(Instant maintenant) {
        exigerModifiable();
        if (statut == StatutVerification.VERIFIE) {
            repasserEnRevue(maintenant);
        }
    }

    /** Pièces à fournir, selon le titulaire déclaré. */
    public Set<TypePiece> piecesExigees() {
        return typeTitulaire == TypeTitulaire.STRUCTURE
            ? Set.of(TypePiece.IDENTITE, TypePiece.JUSTIFICATIF_REVERSEMENT, TypePiece.NINEA_RCCM)
            : Set.of(TypePiece.IDENTITE, TypePiece.JUSTIFICATIF_REVERSEMENT);
    }

    /**
     * Demande d'examen par l'administrateur.
     *
     * @param deposees types des pièces déjà déposées
     * @throws IllegalArgumentException  (400) déclaration ou pièces manquantes
     * @throws BusinessConflictException (409) déjà en revue, vérifié ou suspendu
     */
    public void soumettre(Set<TypePiece> deposees, Instant maintenant) {
        if (statut != StatutVerification.A_VERIFIER && statut != StatutVerification.REFUSE) {
            throw new BusinessConflictException("Ce compte n'attend pas de nouvelle demande de vérification");
        }
        if (typeTitulaire == null || numeroReversement == null || titulaireReversement == null) {
            throw new IllegalArgumentException("Renseignez d'abord le titulaire et le numéro de reversement");
        }
        if (!deposees.containsAll(piecesExigees())) {
            throw new IllegalArgumentException("Pièces manquantes : déposez toutes les pièces demandées");
        }
        repasserEnRevue(maintenant);
    }

    /** @throws BusinessConflictException (409) si le compte n'est pas en revue */
    public void verifier(UUID administrateurId, Instant maintenant) {
        exigerEnRevue();
        statut = StatutVerification.VERIFIE;
        decider(administrateurId, null, maintenant);
    }

    /**
     * @throws IllegalArgumentException  (400) motif absent ou trop long
     * @throws BusinessConflictException (409) si le compte n'est pas en revue
     */
    public void refuser(UUID administrateurId, String motifSaisi, Instant maintenant) {
        exigerEnRevue();
        String m = motifObligatoire(motifSaisi);
        statut = StatutVerification.REFUSE;
        decider(administrateurId, m, maintenant);
    }

    /**
     * Suspension définitive dans l'application (ADR 0012, point 6).
     *
     * @throws IllegalArgumentException  (400) motif absent ou trop long
     * @throws BusinessConflictException (409) déjà suspendu
     */
    public void suspendre(UUID administrateurId, String motifSaisi, Instant maintenant) {
        exigerNonSuspendu();
        String m = motifObligatoire(motifSaisi);
        statut = StatutVerification.SUSPENDU;
        decider(administrateurId, m, maintenant);
    }

    /** Point de départ de la conservation des pièces (5 ans, ADR 0012). */
    public void reversementEnregistre(Instant le) {
        if (le != null && (dernierReversementLe == null || le.isAfter(dernierReversementLe))) {
            dernierReversementLe = le.truncatedTo(ChronoUnit.MICROS);
        }
    }

    public boolean estVerifie() {
        return statut == StatutVerification.VERIFIE;
    }

    /** Mobile sénégalais normalisé en +2217XXXXXXXX. */
    public static String normaliserNumero(String saisi) {
        String chiffres = saisi == null ? "" : saisi.replaceAll("[\\s.-]", "");
        if (chiffres.startsWith("00221")) {
            chiffres = "+" + chiffres.substring(2);
        } else if (chiffres.startsWith("221")) {
            chiffres = "+" + chiffres;
        } else if (!chiffres.startsWith("+")) {
            chiffres = "+221" + chiffres;
        }
        if (!NUMERO.matcher(chiffres).matches()) {
            throw new IllegalArgumentException("Numéro de reversement invalide : mobile sénégalais à 9 chiffres (77 123 45 67)");
        }
        return chiffres;
    }

    private void repasserEnRevue(Instant maintenant) {
        statut = StatutVerification.EN_REVUE;
        soumisLe = maintenant.truncatedTo(ChronoUnit.MICROS);
        motif = null;
        decideLe = null;
        decidePar = null;
    }

    private void decider(UUID administrateurId, String m, Instant maintenant) {
        if (administrateurId == null) {
            throw new IllegalArgumentException("L'administrateur est obligatoire");
        }
        decidePar = administrateurId;
        decideLe = maintenant.truncatedTo(ChronoUnit.MICROS);
        motif = m;
    }

    private void exigerEnRevue() {
        if (statut != StatutVerification.EN_REVUE) {
            throw new BusinessConflictException("Ce compte n'est pas en attente de vérification");
        }
    }

    /**
     * Ni suspendu, ni en revue : pendant l'examen, l'administrateur doit décider sur ce qu'il a
     * vu ; un numéro changé entre-temps serait accepté sans avoir été examiné.
     */
    private void exigerModifiable() {
        exigerNonSuspendu();
        if (statut == StatutVerification.EN_REVUE) {
            throw new BusinessConflictException(
                "Votre demande est en cours d'examen : attendez la décision avant de modifier votre dossier");
        }
    }

    private void exigerNonSuspendu() {
        if (statut == StatutVerification.SUSPENDU) {
            throw new BusinessConflictException("Ce compte est suspendu");
        }
    }

    private static String motifObligatoire(String saisi) {
        String m = nettoyer(saisi);
        if (m == null || m.length() > MOTIF_MAX) {
            throw new IllegalArgumentException("Le motif est obligatoire (500 caractères au plus)");
        }
        return m;
    }

    private static String nettoyer(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }

    public UUID getOrganisateurId() {
        return organisateurId;
    }

    public StatutVerification getStatut() {
        return statut;
    }

    public TypeTitulaire getTypeTitulaire() {
        return typeTitulaire;
    }

    public String getNomStructure() {
        return nomStructure;
    }

    public String getNinea() {
        return ninea;
    }

    public String getNumeroReversement() {
        return numeroReversement;
    }

    public String getTitulaireReversement() {
        return titulaireReversement;
    }

    public Instant getSoumisLe() {
        return soumisLe;
    }

    public Instant getDecideLe() {
        return decideLe;
    }

    public UUID getDecidePar() {
        return decidePar;
    }

    public String getMotif() {
        return motif;
    }

    public Instant getDernierReversementLe() {
        return dernierReversementLe;
    }
}
