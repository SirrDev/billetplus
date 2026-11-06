package com.billetplus.auth.infrastructure;

import com.billetplus.auth.application.UserRepository;
import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le premier compte ADMIN au démarrage, HORS API : aucune route ne peut
 * créer un ADMIN (correctif /register), et aucun mot de passe ni hash n'est
 * commité. Les identifiants viennent de variables d'environnement :
 * BILLETPLUS_ADMIN_EMAIL et BILLETPLUS_ADMIN_MOT_DE_PASSE (propriétés
 * billetplus.admin.email et billetplus.admin.mot-de-passe).
 *
 * Sans ces variables, rien n'est fait (cas normal en dev et en CI). Dès qu'un
 * ADMIN existe, rien n'est fait non plus : les variables peuvent alors être
 * retirées. Une configuration invalide (une seule des deux variables, mot de
 * passe hors 8–72 caractères) fait échouer le démarrage : mieux vaut une
 * erreur visible qu'un compte d'administration créé avec un mot de passe faible.
 *
 * Le mot de passe n'est jamais journalisé.
 */
@Component
class PremierAdminInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PremierAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String motDePasse;

    PremierAdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            @Value("${billetplus.admin.email:}") String email,
                            @Value("${billetplus.admin.mot-de-passe:}") String motDePasse) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim();
        this.motDePasse = motDePasse == null ? "" : motDePasse;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() && motDePasse.isEmpty()) {
            return;
        }
        if (email.isEmpty() || motDePasse.isEmpty()) {
            throw new IllegalStateException(
                "BILLETPLUS_ADMIN_EMAIL et BILLETPLUS_ADMIN_MOT_DE_PASSE doivent être définis ensemble");
        }
        if (motDePasse.length() < 8 || motDePasse.length() > 72) {
            throw new IllegalStateException(
                "BILLETPLUS_ADMIN_MOT_DE_PASSE doit faire entre 8 et 72 caractères");
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            LOG.info("Un compte ADMIN existe déjà : aucun compte créé (les variables BILLETPLUS_ADMIN_* peuvent être retirées)");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                "BILLETPLUS_ADMIN_EMAIL est déjà utilisé par un compte qui n'est pas ADMIN");
        }
        userRepository.save(new User(email, passwordEncoder.encode(motDePasse), "Administrateur", Role.ADMIN));
        // Sans l'e-mail : c'est l'identifiant du compte le plus privilégié, et les journaux
        // partent souvent vers un outil tiers.
        LOG.info("Premier compte ADMIN créé");
    }
}
