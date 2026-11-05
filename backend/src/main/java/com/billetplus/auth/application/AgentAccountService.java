package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import com.billetplus.shared.evenements.AgentRetire;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
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
    private final ApplicationEventPublisher publisher;

    public AgentAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher publisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.publisher = publisher;
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

    /**
     * Corrige le nom et l'e-mail d'un de SES agents actifs. Les jetons déjà émis restent
     * valides : ils portent l'identifiant du compte, qui ne change pas.
     *
     * @throws NoSuchElementException   si l'agent est inconnu, retiré ou d'un autre organisateur
     * @throws IllegalArgumentException si l'email est déjà utilisé par un autre compte
     */
    @Transactional
    public User modifierAgent(UUID organisateurId, UUID agentId, String email, String nomComplet) {
        verifierOrganisateur(organisateurId);
        User agent = agentDe(organisateurId, agentId);
        if (!agent.estActif()) {
            throw agentIntrouvable(agentId);
        }
        boolean emailChange = !User.normaliserEmail(email).equals(agent.getEmail());
        if (emailChange && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }
        agent.modifierIdentite(email, nomComplet);
        return userRepository.save(agent);
    }

    /**
     * Retire un de SES agents : compte désactivé (jamais supprimé, pour l'historique des scans)
     * et affectations supprimées dans la même transaction par {@link AgentRetire} (ADR 0005).
     * Idempotent : retirer un agent déjà retiré ne change rien. Son e-mail reste réservé.
     *
     * @throws NoSuchElementException si l'agent est inconnu ou d'un autre organisateur
     */
    @Transactional
    public void retirerAgent(UUID organisateurId, UUID agentId) {
        verifierOrganisateur(organisateurId);
        User agent = agentDe(organisateurId, agentId);
        if (agent.estActif()) {
            agent.desactiver();
            userRepository.save(agent);
            publisher.publishEvent(new AgentRetire(agentId));
        }
    }

    /** Agents actifs créés par cet organisateur. */
    @Transactional(readOnly = true)
    public List<User> listerMesAgents(UUID organisateurId) {
        verifierOrganisateur(organisateurId);
        return userRepository.findByCreePar(organisateurId).stream()
            .filter(user -> user.getRole() == Role.AGENT && user.estActif())
            .toList();
    }

    /** Même réponse pour un agent inconnu ou celui d'un autre : ne rien révéler de ses comptes. */
    private User agentDe(UUID organisateurId, UUID agentId) {
        return userRepository.findById(agentId)
            .filter(user -> user.getRole() == Role.AGENT && organisateurId.equals(user.getCreePar()))
            .orElseThrow(() -> agentIntrouvable(agentId));
    }

    private static NoSuchElementException agentIntrouvable(UUID agentId) {
        return new NoSuchElementException("Agent introuvable : " + agentId);
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
