package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.UserRepository;
import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur du port UserRepository. {@code @Component} et non {@code @Repository} : la
 * traduction d'exceptions des beans {@code @Repository} convertirait l'IllegalArgumentException
 * levée ici en InvalidDataAccessApiUsageException, donc le 400 en 500 (piège du CLAUDE.md).
 */
@Component
class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springRepo;

    JpaUserRepositoryAdapter(SpringDataUserRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springRepo.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springRepo.findByEmail(email.toLowerCase().trim());
    }

    /** Contrainte d'unicité de users.email (V2). */
    private static final String CONTRAINTE_EMAIL_UNIQUE = "uq_users_email";

    /**
     * Écrit tout de suite ({@code saveAndFlush}) : un doublon d'e-mail entre deux requêtes
     * simultanées (toutes deux passées par {@code existsByEmail}) n'apparaît qu'à l'écriture.
     * Sans flush, il tomberait au commit, hors de toute gestion, et l'API répondrait 500.
     * Ici, il devient le même 400 qu'un doublon ordinaire : aucune différence observable.
     *
     * @throws IllegalArgumentException si l'e-mail est déjà pris par un autre compte
     */
    @Override
    public User save(User user) {
        try {
            return springRepo.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (violeLaContrainte(e, CONTRAINTE_EMAIL_UNIQUE)) {
                throw new IllegalArgumentException("Un compte existe déjà avec cet email", e);
            }
            throw e;
        }
    }

    /**
     * Nom de contrainte donné par Hibernate, et non un texte cherché dans le message : le
     * détail PostgreSQL (« Failing row contains ... ») recopie des valeurs saisies, qui
     * pourraient contenir ce nom. À garder aligné sur V2 si la contrainte change de nom.
     */
    private static boolean violeLaContrainte(Throwable erreur, String contrainte) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return contrainte.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    @Override
    public boolean existsByEmail(String email) {
        return springRepo.existsByEmail(email.toLowerCase().trim());
    }

    @Override
    public List<User> findByCreePar(UUID createurId) {
        return springRepo.findByCreeParOrderByCreeLeAsc(createurId);
    }

    @Override
    public boolean existsByRole(Role role) {
        return springRepo.existsByRole(role);
    }
}
