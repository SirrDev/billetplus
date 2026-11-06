package com.billetplus.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Module events — niveau 2 (JPA-as-domain), voir ADR 0002. Une ville de la liste
 * fermée tenue par Billet+ (ADR 0011, table villes remplie par migration). Jamais
 * créée ni modifiée par l'application.
 */
@Entity
@Immutable
@Table(name = "villes")
public class Ville {

    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Pays pays;

    @Column(nullable = false)
    private int ordre;

    protected Ville() {
        // requis par JPA
    }

    /** Une ville telle qu'elle existe en base (tests) ; l'application ne crée jamais de ville. */
    public Ville(String code, String nom, Pays pays, int ordre) {
        this.code = code;
        this.nom = nom;
        this.pays = pays;
        this.ordre = ordre;
    }

    /** ADR 0011, point 9 : seule une ville du Sénégal permet la mise en vente pour l'instant. */
    public boolean ouverteALaVente() {
        return pays == Pays.SN;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public Pays getPays() {
        return pays;
    }

    public int getOrdre() {
        return ordre;
    }
}
