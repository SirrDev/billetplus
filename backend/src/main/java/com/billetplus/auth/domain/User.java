package com.billetplus.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Module auth — niveau 2 (JPA-as-domain), voir ADR 0002.
 * CRUD à faible risque métier : pas de domaine séparé de l'entité JPA,
 * contrairement à tickets/orders (niveau 3).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String motDePasseHash;

    @Column(nullable = false)
    private String nomComplet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant creeLe;

    /**
     * Organisateur qui a créé ce compte : renseigné pour un AGENT (contrainte V6
     * chk_users_agent_a_un_createur), nul pour un compte inscrit ou un ADMIN.
     * Référence la même table, donc le même module : FK autorisée.
     */
    @Column(updatable = false)
    private UUID creePar;

    /** Date du retrait d'un agent par son organisateur (V14). Null : compte actif. */
    private Instant desactiveLe;

    /**
     * Verrouillage optimiste (V15). Hibernate réécrit toute la ligne : sans version, un
     * changement de mot de passe concurrent d'un retrait remettait desactive_le à null.
     * Conflit → 409 (OptimisticLockingFailureException, GlobalExceptionHandler).
     * Wrapper null à la création : persist au lieu de merge.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected User() {
        // requis par JPA
    }

    public User(String email, String motDePasseHash, String nomComplet, Role role) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire");
        }
        if (motDePasseHash == null || motDePasseHash.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire");
        }
        this.id = UUID.randomUUID();
        this.email = normaliserEmail(email);
        this.motDePasseHash = motDePasseHash;
        this.nomComplet = nomComplet;
        this.role = role;
        this.creeLe = Instant.now();
    }

    /**
     * Compte AGENT créé par un organisateur, qui en est propriétaire. Le rôle est
     * fixé ici, jamais choisi par l'appelant (même principe que l'inscription).
     *
     * @throws IllegalArgumentException si l'organisateur est absent
     */
    public static User agentCreePar(UUID organisateurId, String email, String motDePasseHash, String nomComplet) {
        if (organisateurId == null) {
            throw new IllegalArgumentException("Un agent a toujours un organisateur créateur");
        }
        User agent = new User(email, motDePasseHash, nomComplet, Role.AGENT);
        agent.creePar = organisateurId;
        return agent;
    }

    /**
     * Remplace le hash du mot de passe. Le contrôle de l'ancien mot de passe et le
     * hachage sont faits par AuthService : l'entité ne connaît pas l'encodeur.
     */
    public void changerMotDePasse(String nouveauHash) {
        if (nouveauHash == null || nouveauHash.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire");
        }
        this.motDePasseHash = nouveauHash;
    }

    /**
     * Corrige le nom et l'e-mail de connexion. L'unicité de l'e-mail est contrôlée par le service.
     *
     * @throws IllegalArgumentException si l'e-mail ou le nom est vide
     */
    public void modifierIdentite(String email, String nomComplet) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire");
        }
        if (nomComplet == null || nomComplet.isBlank()) {
            throw new IllegalArgumentException("Le nom est obligatoire");
        }
        this.email = normaliserEmail(email);
        this.nomComplet = nomComplet;
    }

    /**
     * Désactive un compte AGENT : il ne peut plus se connecter ni rafraîchir ses jetons. Le
     * compte reste en base, pour l'historique des scans. Sans effet s'il est déjà désactivé.
     *
     * @throws IllegalStateException si le compte n'est pas un agent (défaut interne : 500)
     */
    public void desactiver() {
        if (role != Role.AGENT) {
            throw new IllegalStateException("Seul un compte agent peut être désactivé");
        }
        if (desactiveLe == null) {
            desactiveLe = Instant.now();
        }
    }

    public boolean estActif() {
        return desactiveLe == null;
    }

    /** Forme stockée d'un e-mail, pour comparer une saisie à la valeur enregistrée. */
    public static String normaliserEmail(String email) {
        return email.toLowerCase().trim();
    }

    public UUID getCreePar() {
        return creePar;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public String getNomComplet() {
        return nomComplet;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public boolean estOrganisateur() {
        return role == Role.ORGANISATEUR;
    }

    public boolean estAgent() {
        return role == Role.AGENT;
    }

    public boolean estAdmin() {
        return role == Role.ADMIN;
    }
}
