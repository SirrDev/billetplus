package com.billetplus.auth.api;

import com.billetplus.auth.api.dto.*;
import com.billetplus.auth.application.AuthService;
import com.billetplus.auth.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.TokenPair tokens = authService.login(request.email(), request.motDePasse());
        return ResponseEntity.ok(TokenResponse.of(tokens.accessToken(), tokens.refreshToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.TokenPair tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(TokenResponse.of(tokens.accessToken(), tokens.refreshToken()));
    }

    /**
     * Inscription publique (route sans jeton, voir SecurityConfig) : crée toujours un
     * compte ORGANISATEUR. Le client ne peut pas choisir son rôle — RegisterRequest
     * n'a pas de champ « role » — donc il ne peut pas s'attribuer ADMIN ou AGENT.
     *
     * Minimal au MVP : pas de validation d'email. À durcir en V2 si l'auto-inscription
     * est ouverte aux acheteurs (rôle dédié, vérification de l'adresse).
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.registerOrganisateur(
            request.email(),
            request.motDePasse(),
            request.nomComplet()
        );
        return ResponseEntity.created(java.net.URI.create("/api/users/" + user.getId())).build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        User user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(MeResponse.from(user));
    }
}
