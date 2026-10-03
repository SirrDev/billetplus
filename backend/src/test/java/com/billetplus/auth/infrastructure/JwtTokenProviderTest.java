package com.billetplus.auth.infrastructure;

import com.billetplus.auth.domain.Role;
import com.billetplus.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test_secret_at_least_32_characters_long_for_hs256");
        properties.setExpirationMinutes(15);
        properties.setRefreshExpirationDays(7);

        provider = new JwtTokenProvider(properties);
        user = new User("test@example.com", "hash", "Test User", Role.ORGANISATEUR);
    }

    @Test
    void genere_un_access_token_valide_et_reconnu_comme_tel() {
        String token = provider.generateAccessToken(user);

        Claims claims = provider.parseAndValidate(token);

        assertThat(provider.isAccessToken(claims)).isTrue();
        assertThat(provider.isRefreshToken(claims)).isFalse();
        assertThat(provider.getRole(claims)).isEqualTo(Role.ORGANISATEUR);
    }

    @Test
    void genere_un_refresh_token_distinct_du_access_token() {
        String token = provider.generateRefreshToken(user);

        Claims claims = provider.parseAndValidate(token);

        assertThat(provider.isRefreshToken(claims)).isTrue();
        assertThat(provider.isAccessToken(claims)).isFalse();
    }

    @Test
    void rejette_un_token_signe_avec_une_autre_cle() {
        JwtProperties autreProperties = new JwtProperties();
        autreProperties.setSecret("une_cle_totalement_differente_de_32_caracteres_minimum");
        JwtTokenProvider autreProvider = new JwtTokenProvider(autreProperties);

        String tokenForge = autreProvider.generateAccessToken(user);

        assertThatThrownBy(() -> provider.parseAndValidate(tokenForge))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void rejette_un_token_corrompu() {
        assertThatThrownBy(() -> provider.parseAndValidate("ceci.nest.pas.un.jwt.valide"))
            .isInstanceOf(JwtException.class);
    }
}
