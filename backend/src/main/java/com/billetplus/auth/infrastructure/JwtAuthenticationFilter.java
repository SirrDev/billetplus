package com.billetplus.auth.infrastructure;

import com.billetplus.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = tokenProvider.parseAndValidate(token);

                if (tokenProvider.isAccessToken(claims)) {
                    UUID userId = tokenProvider.getUserId(claims);
                    Role role = tokenProvider.getRole(claims);

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
                    var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                // Un refresh token présenté sur un endpoint protégé est simplement
                // ignoré ici — la requête continuera non authentifiée et sera
                // rejetée en 401 par Spring Security, sans distinction d'erreur
                // qui aiderait un attaquant à deviner le type de token attendu.
            } catch (JwtException ignored) {
                // Token invalide/expiré : on laisse la requête continuer non authentifiée,
                // Spring Security la rejettera en 401 si l'endpoint est protégé.
            }
        }

        chain.doFilter(request, response);
    }
}
