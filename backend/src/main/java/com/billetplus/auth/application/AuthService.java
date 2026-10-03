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

    @Transactional
    public User register(String email, String motDePasseClair, String nomComplet, Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }
        String hash = passwordEncoder.encode(motDePasseClair);
        User user = new User(email, hash, nomComplet, role);
        return userRepository.save(user);
    }
}
