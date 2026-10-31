package com.billetplus.auth.application;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import com.billetplus.auth.infrastructure.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional(readOnly = true)
    public TokenPair login(String email, String motDePasse) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        if (!passwordEncoder.matches(motDePasse, user.getMotDePasseHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }

        return new TokenPair(
            tokenProvider.generateAccessToken(user),
            tokenProvider.generateRefreshToken(user)
        );
    }

    /**
     * Émet un nouveau couple de tokens à partir d'un refresh token valide.
     *
     * Limite assumée au MVP : le refresh token n'est pas stocké côté serveur,
     * donc il ne peut pas être révoqué avant son expiration naturelle
     * (7 jours). Acceptable au MVP, à revisiter en V2 si un besoin de
     * déconnexion forcée (compte compromis) apparaît.
     */
    @Transactional(readOnly = true)
    public TokenPair refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parseAndValidate(refreshToken);
        } catch (JwtException e) {
            throw new BadCredentialsException("Refresh token invalide ou expiré");
        }

        if (!tokenProvider.isRefreshToken(claims)) {
            throw new BadCredentialsException("Ce token n'est pas un refresh token");
        }

        UUID userId = tokenProvider.getUserId(claims);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BadCredentialsException("Utilisateur introuvable"));

        return new TokenPair(
            tokenProvider.generateAccessToken(user),
            tokenProvider.generateRefreshToken(user)
        );
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Utilisateur introuvable"));
    }

    /**
     * Change le mot de passe du compte du jeton, après vérification de l'actuel.
     *
     * Mot de passe actuel faux → IllegalArgumentException (400) et non
     * BadCredentialsException (401) : un 401 ferait croire au client que sa session a
     * expiré (il tenterait un rafraîchissement, puis effacerait ses jetons).
     *
     * Limite assumée au MVP (même que {@link #refresh}) : les jetons déjà émis restent
     * valides jusqu'à leur expiration (15 min pour l'accès, 7 jours pour le
     * rafraîchissement). Changer de mot de passe ne déconnecte pas les autres appareils.
     *
     * @throws java.util.NoSuchElementException si le compte n'existe plus (404)
     * @throws IllegalArgumentException si l'actuel est faux ou si le nouveau est identique (400)
     */
    @Transactional
    public void changerMotDePasse(UUID userId, String motDePasseActuel, String nouveauMotDePasse) {
        User user = getCurrentUser(userId);
        if (!passwordEncoder.matches(motDePasseActuel, user.getMotDePasseHash())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect");
        }
        if (nouveauMotDePasse.equals(motDePasseActuel)) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel");
        }
        user.changerMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        userRepository.save(user);
    }

    /**
     * Inscription publique (route accessible sans jeton) : le seul rôle qu'un
     * visiteur anonyme peut obtenir est ORGANISATEUR.
     *
     * Le rôle est volontairement fixé ICI et non fourni par l'appelant : aucune
     * méthode publique de ce service n'accepte un Role en paramètre, donc aucune
     * route ne peut, par erreur, laisser un client s'attribuer ADMIN ou AGENT.
     * Les comptes AGENT (créés par un organisateur) et ADMIN (créé hors API) auront
     * chacun leur propre cas d'usage, avec son propre contrôle d'accès, quand le
     * besoin se présentera — voir HANDOFF.md, section 7.
     *
     * @throws IllegalArgumentException si un compte existe déjà avec cet email (traduit en HTTP 400)
     */
    @Transactional
    public User registerOrganisateur(String email, String motDePasseClair, String nomComplet) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }
        String hash = passwordEncoder.encode(motDePasseClair);
        User user = new User(email, hash, nomComplet, Role.ORGANISATEUR);
        return userRepository.save(user);
    }
}
