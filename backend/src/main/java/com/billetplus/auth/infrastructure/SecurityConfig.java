package com.billetplus.auth.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // active @PreAuthorize — utilisé dès events/api/*
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless consommée par la PWA, pas de session cookie
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sans point d'entrée, Spring Security 6 répond 403 à un anonyme. La PWA a besoin
                // d'un 401 pour savoir qu'il faut rafraîchir ou redemander le token.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Seuls login/refresh/register sont publics — /me exige un token valide.
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/register").permitAll()
                        // Les règles sont évaluées dans l'ordre, la première qui correspond gagne :
                        // « mine » doit donc précéder la règle publique GET /api/events/** ci-dessous.
                        .requestMatchers(HttpMethod.GET, "/api/events/mine").authenticated()
                        // Parcourir les événements/lieux ne requiert pas de compte (acheteur anonyme).
                        // Créer/publier/annuler reste protégé par @PreAuthorize sur les controllers.
                        .requestMatchers(HttpMethod.GET, "/api/events/**", "/api/venues/**").permitAll()
                        // Achat sans compte (ADR 0006) : créer une commande et la relire avec son
                        // jeton ne demandent aucun compte. Le jeton est contrôlé par le cas d'usage.
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/paiement").permitAll()
                        // Webhook de la passerelle : authentifié par la signature HMAC du corps (ADR 0007).
                        .requestMatchers(HttpMethod.POST, "/api/paiements/unitechpay/webhook").permitAll()
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Tomcat redirige vers /error pour produire les réponses 4xx/5xx. Sans cette règle,
                        // cette redirection interne est elle aussi soumise à l'authentification et tout
                        // statut d'erreur devient un 403 pour un client non connecté.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}