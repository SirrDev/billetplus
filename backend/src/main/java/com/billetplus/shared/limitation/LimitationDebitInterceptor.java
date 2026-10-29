package com.billetplus.shared.limitation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Clock;
import java.util.List;

/**
 * Limitation de débit des routes sensibles (création de commande, connexion,
 * scan...), selon les règles de {@link LimitationDebitProperties}.
 *
 * Intercepteur MVC, et non filtre de servlet : il s'exécute APRÈS Spring
 * Security, donc il connaît le compte authentifié (clé COMPTE), et AVANT la
 * lecture et la validation du corps, donc une requête invalide compte aussi.
 *
 * Fenêtres fixes, alignées sur l'horloge : simple et sans état à nettoyer. Un
 * demandeur peut envoyer jusqu'à deux fois la limite à cheval sur deux fenêtres ;
 * acceptable pour freiner un script, pas pour un quota exact.
 *
 * Adresse IP : request.getRemoteAddr(), jamais l'en-tête X-Forwarded-For lu
 * directement (n'importe quel client peut l'écrire). Derrière un proxy, c'est
 * server.forward-headers-strategy qui doit la renseigner (préparation de la
 * production).
 *
 * Panne de Redis : la requête passe (WARN dans le journal). Le limiteur freine
 * les abus, il ne doit pas à lui seul arrêter les ventes.
 */
class LimitationDebitInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(LimitationDebitInterceptor.class);
    private static final String PREFIXE_CLE = "limitation:";

    private record RegleCompilee(LimitationDebitProperties.Regle regle, PathPattern motif) {
    }

    private final List<RegleCompilee> regles;
    private final CompteurRequetes compteur;
    private final Clock clock;

    LimitationDebitInterceptor(LimitationDebitProperties proprietes, CompteurRequetes compteur, Clock clock) {
        PathPatternParser parseur = new PathPatternParser();
        this.regles = proprietes.regles().stream()
            .map(r -> new RegleCompilee(r, parseur.parse(r.chemin())))
            .toList();
        this.compteur = compteur;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        PathContainer chemin = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
        for (RegleCompilee compilee : regles) {
            LimitationDebitProperties.Regle regle = compilee.regle();
            if (regle.methode().equals(request.getMethod()) && compilee.motif().matches(chemin)) {
                verifier(regle, demandeur(regle, request));
            }
        }
        return true;
    }

    private void verifier(LimitationDebitProperties.Regle regle, String demandeur) {
        long fenetreMs = regle.fenetre().toMillis();
        long maintenantMs = clock.millis();
        long indexFenetre = maintenantMs / fenetreMs;
        String cle = PREFIXE_CLE + regle.nom() + ":" + demandeur + ":" + indexFenetre;
        long total;
        try {
            total = compteur.incrementer(cle, regle.fenetre());
        } catch (DataAccessException e) {
            LOG.warn("Limitation de débit inopérante (Redis indisponible) pour la règle {} : requête acceptée",
                regle.nom(), e);
            return;
        }
        if (total > regle.limite()) {
            // Secondes restantes jusqu'à la fin de la fenêtre, arrondies au-dessus (au moins 1).
            long restantMs = (indexFenetre + 1) * fenetreMs - maintenantMs;
            throw new TropDeRequetesException(Math.max(1, (restantMs + 999) / 1000));
        }
    }

    /** Compte authentifié pour une règle COMPTE (principal = UUID, voir JwtAuthenticationFilter), sinon l'IP. */
    private static String demandeur(LimitationDebitProperties.Regle regle, HttpServletRequest request) {
        if (regle.cle() == LimitationDebitProperties.Cle.COMPTE) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                return "compte:" + auth.getName();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
