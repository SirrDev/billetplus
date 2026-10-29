package com.billetplus.shared.limitation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Règles de limitation de débit (billetplus.limitation dans application.yml).
 *
 * Chaque règle est vérifiée au démarrage : une règle mal écrite empêche
 * l'application de démarrer, au lieu de laisser une route sans protection.
 *
 * @param active false désactive toute limitation (jamais en production)
 */
@ConfigurationProperties(prefix = "billetplus.limitation")
public record LimitationDebitProperties(@DefaultValue("true") boolean active, @DefaultValue List<Regle> regles) {

    private static final Set<String> METHODES = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    public LimitationDebitProperties {
        regles = List.copyOf(regles);
        Set<String> noms = new HashSet<>();
        for (Regle regle : regles) {
            if (!noms.add(regle.nom())) {
                // Le nom fait partie de la clé Redis : deux règles du même nom partageraient leur compteur.
                throw new IllegalStateException("Règle de limitation en double : " + regle.nom());
            }
        }
    }

    /** Ce qui identifie le demandeur pour une règle. */
    public enum Cle {
        /** Adresse IP du client, telle que la voit le serveur (request.getRemoteAddr()). */
        IP,
        /** Compte authentifié ; à défaut (anonyme), l'adresse IP. */
        COMPTE
    }

    /**
     * @param nom     identifiant court, unique, utilisé dans la clé Redis
     * @param methode méthode HTTP (GET, POST...)
     * @param chemin  motif de chemin Spring (par exemple /api/events/{@literal *}/scans)
     * @param limite  nombre de requêtes autorisées par fenêtre, au moins 1
     * @param fenetre durée de la fenêtre, au moins une seconde
     */
    public record Regle(String nom, String methode, String chemin, Cle cle, int limite, Duration fenetre) {

        public Regle {
            if (nom == null || !nom.matches("[a-z0-9-]{1,40}")) {
                throw new IllegalStateException("Nom de règle de limitation invalide : " + nom);
            }
            if (methode == null || !METHODES.contains(methode.toUpperCase(Locale.ROOT))) {
                throw new IllegalStateException("Méthode HTTP invalide pour la règle " + nom + " : " + methode);
            }
            methode = methode.toUpperCase(Locale.ROOT);
            if (chemin == null || !chemin.startsWith("/")) {
                throw new IllegalStateException("Chemin invalide pour la règle " + nom + " : " + chemin);
            }
            if (cle == null) {
                throw new IllegalStateException("Clé (IP ou COMPTE) obligatoire pour la règle " + nom);
            }
            if (limite < 1) {
                throw new IllegalStateException("La limite de la règle " + nom + " doit être au moins 1");
            }
            if (fenetre == null || fenetre.compareTo(Duration.ofSeconds(1)) < 0) {
                throw new IllegalStateException("La fenêtre de la règle " + nom + " doit durer au moins une seconde");
            }
        }
    }
}
