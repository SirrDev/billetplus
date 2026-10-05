package com.billetplus.events.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Module events — niveau 2 (JPA-as-domain), voir ADR 0002.
 *
 * Choix assumé : latitude/longitude en colonnes simples plutôt qu'un type
 * PostGIS geography(Point,4326) mappé en JPA. L'extension PostGIS est déjà
 * activée (V1__enable_extensions.sql, voir ADR 0003), mais l'ajout du type
 * spatial complet (hibernate-spatial) est différé jusqu'à l'implémentation
 * réelle de la recherche géolocalisée (feature/web-search-events, SHOULD).
 * Migration non bloquante : une colonne geography pourra être ajoutée plus
 * tard sans toucher aux colonnes existantes.
 */
@Entity
@Table(name = "venues")
public class Venue {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String adresse;

    private Double latitude;
    private Double longitude;

    /**
     * Plan de salle — structure JSON libre, propre à chaque lieu.
     * Pas de modèle Java dédié tant que feature/web-event-detail-checkout
     * (plan de salle interactif, SHOULD) n'est pas construite ; stocké tel
     * quel, lu/écrit comme chaîne JSON brute par l'infrastructure.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String seatingPlan;

    protected Venue() {
        // requis par JPA
    }

    public Venue(String nom, String adresse, Double latitude, Double longitude) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom du lieu est obligatoire");
        }
        if (adresse == null || adresse.isBlank()) {
            throw new IllegalArgumentException("L'adresse est obligatoire");
        }
        this.id = UUID.randomUUID();
        this.nom = nom;
        this.adresse = adresse;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void definirPlanDeSalle(String seatingPlanJson) {
        this.seatingPlan = seatingPlanJson;
    }

    public UUID getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getAdresse() {
        return adresse;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getSeatingPlan() {
        return seatingPlan;
    }
}
