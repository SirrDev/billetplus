package com.billetplus.shared.limitation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitationDebitInterceptorTest {

    /** Horloge que le test fait avancer. */
    private static final class HorlogeReglable extends Clock {
        private Instant maintenant;

        HorlogeReglable(Instant depart) {
            this.maintenant = depart;
        }

        void avancer(Duration duree) {
            maintenant = maintenant.plus(duree);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    /** Compteur en mémoire, avec les mêmes clés que Redis (l'expiration est simulée par l'index de fenêtre). */
    private static final class CompteurEnMemoire implements CompteurRequetes {
        final Map<String, Long> compteurs = new HashMap<>();
        boolean enPanne;

        @Override
        public long incrementer(String cle, Duration fenetre) {
            if (enPanne) {
                throw new QueryTimeoutException("Redis indisponible");
            }
            return compteurs.merge(cle, 1L, Long::sum);
        }
    }

    // Début d'une fenêtre d'une minute : 10:00:00 pile.
    private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-11-01T10:00:00Z"));
    private final CompteurEnMemoire compteur = new CompteurEnMemoire();
    private final LimitationDebitInterceptor intercepteur = new LimitationDebitInterceptor(
        new LimitationDebitProperties(true, List.of(
            new LimitationDebitProperties.Regle("commande", "POST", "/api/orders",
                LimitationDebitProperties.Cle.IP, 3, Duration.ofMinutes(1)),
            new LimitationDebitProperties.Regle("scan", "POST", "/api/events/*/scans",
                LimitationDebitProperties.Cle.COMPTE, 2, Duration.ofMinutes(1)))),
        compteur, horloge);

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    private void appeler(String methode, String chemin, String ip) {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        requete.setRemoteAddr(ip);
        intercepteur.preHandle(requete, new MockHttpServletResponse(), new Object());
    }

    private static void connecter(UUID compte) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            compte, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
    }

    @Test
    void au_dela_de_la_limite_la_requete_est_refusee_jusqu_a_la_fin_de_la_fenetre() {
        for (int i = 0; i < 3; i++) {
            appeler("POST", "/api/orders", "203.0.113.1");
        }
        horloge.avancer(Duration.ofSeconds(20));

        assertThatThrownBy(() -> appeler("POST", "/api/orders", "203.0.113.1"))
            .isInstanceOfSatisfying(TropDeRequetesException.class,
                e -> assertThat(e.getReessayerDansSecondes()).isEqualTo(40));

        // Fenêtre suivante : le compteur repart de zéro.
        horloge.avancer(Duration.ofSeconds(40));
        appeler("POST", "/api/orders", "203.0.113.1");
    }

    @Test
    void retry_after_est_arrondi_a_la_seconde_superieure_et_vaut_au_moins_un() {
        for (int i = 0; i < 3; i++) {
            appeler("POST", "/api/orders", "203.0.113.1");
        }
        horloge.avancer(Duration.ofMillis(59_999));

        assertThatThrownBy(() -> appeler("POST", "/api/orders", "203.0.113.1"))
            .isInstanceOfSatisfying(TropDeRequetesException.class,
                e -> assertThat(e.getReessayerDansSecondes()).isEqualTo(1));
        horloge.avancer(Duration.ofMillis(1));
        appeler("POST", "/api/orders", "203.0.113.1");
        // 1,5 s restante → 2 s.
        appeler("POST", "/api/orders", "203.0.113.1");
        appeler("POST", "/api/orders", "203.0.113.1");
        horloge.avancer(Duration.ofMillis(58_500));
        assertThatThrownBy(() -> appeler("POST", "/api/orders", "203.0.113.1"))
            .isInstanceOfSatisfying(TropDeRequetesException.class,
                e -> assertThat(e.getReessayerDansSecondes()).isEqualTo(2));
    }

    @Test
    void chaque_adresse_ip_a_son_propre_compteur() {
        for (int i = 0; i < 3; i++) {
            appeler("POST", "/api/orders", "203.0.113.1");
        }
        appeler("POST", "/api/orders", "203.0.113.2");
        assertThatThrownBy(() -> appeler("POST", "/api/orders", "203.0.113.1"))
            .isInstanceOf(TropDeRequetesException.class);
    }

    @Test
    void une_autre_methode_ou_un_autre_chemin_ne_sont_pas_limites() {
        for (int i = 0; i < 10; i++) {
            appeler("GET", "/api/orders", "203.0.113.1");
            appeler("POST", "/api/orders/extra", "203.0.113.1");
            appeler("POST", "/api/events", "203.0.113.1");
        }
        assertThat(compteur.compteurs).isEmpty();
    }

    @Test
    void une_regle_compte_suit_le_compte_quelle_que_soit_l_adresse_ip() {
        UUID agent = UUID.randomUUID();
        connecter(agent);
        appeler("POST", "/api/events/" + UUID.randomUUID() + "/scans", "203.0.113.1");
        appeler("POST", "/api/events/" + UUID.randomUUID() + "/scans", "203.0.113.2");

        assertThatThrownBy(() -> appeler("POST", "/api/events/" + UUID.randomUUID() + "/scans", "203.0.113.3"))
            .isInstanceOf(TropDeRequetesException.class);
        assertThat(compteur.compteurs.keySet()).allMatch(cle -> cle.contains("compte:" + agent));

        // Un autre compte n'est pas touché.
        connecter(UUID.randomUUID());
        appeler("POST", "/api/events/" + UUID.randomUUID() + "/scans", "203.0.113.1");
    }

    @Test
    void une_regle_compte_retombe_sur_l_adresse_ip_pour_un_anonyme() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "cle", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        appeler("POST", "/api/events/" + UUID.randomUUID() + "/scans", "203.0.113.9");

        assertThat(compteur.compteurs.keySet()).singleElement()
            .satisfies(cle -> assertThat(cle).startsWith("limitation:scan:ip:203.0.113.9:"));
    }

    @Test
    void si_redis_est_en_panne_la_requete_passe() {
        compteur.enPanne = true;
        for (int i = 0; i < 10; i++) {
            appeler("POST", "/api/orders", "203.0.113.1");
        }
    }
}
