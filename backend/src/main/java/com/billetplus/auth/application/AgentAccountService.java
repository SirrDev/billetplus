package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Comptes AGENT (contrôle d'entrée), créés par un organisateur qui en est
 * propriétaire. Séparé d'AuthService pour respecter sa règle : aucune méthode
 * publique n'accepte un Role, le rôle est toujours fixé par le serveur
 * (ici AGENT, par User.agentCreePar).
 */
@Service
public class AgentAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AgentAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @param organisateurId l'utilisateur authentifié (tiré du jeton, jamais de la requête)
     * @throws AccessDeniedException     si le demandeur n'est pas un organisateur (vérifié en base,
     *                                   en plus du @PreAuthorize de la route)
     * @throws IllegalArgumentException si l'email est déjà utilisé (même message qu'à l'inscription)
     */
    @Transactional
    public User creerAgent(UUID organisateurId, String email, String motDePasseClair, String nomComplet) {
        verifierOrganisateur(organisateurId);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }
        User agent = User.agentCreePar(organisateurId, email, passwordEncoder.encode(motDePasseClair), nomComplet);
        return userRepository.save(agent);
    }

    /** Agents créés par cet organisateur. */
    @Transactional(readOnly = true)
    public List<User> listerMesAgents(UUID organisateurId) {
        verifierOrganisateur(organisateurId);
        return userRepository.findByCreePar(organisateurId).stream()
            .filter(user -> user.getRole() == Role.AGENT)
            .toList();
    }

    private void verifierOrganisateur(UUID organisateurId) {
        boolean organisateur = organisateurId != null && userRepository.findById(organisateurId)
            .map(user -> user.getRole() == Role.ORGANISATEUR)
            .orElse(false);
        if (!organisateur) {
            throw new AccessDeniedException("Seul un organisateur peut gérer des agents");
        }
    }
}
