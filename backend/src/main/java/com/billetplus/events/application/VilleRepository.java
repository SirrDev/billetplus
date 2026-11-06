package com.billetplus.events.application;

import com.billetplus.events.domain.Ville;

import java.util.List;
import java.util.Optional;

/** Port du module events — liste fermée des villes (ADR 0011), en lecture seule. */
public interface VilleRepository {

    Optional<Ville> findByCode(String code);

    /** Toutes les villes, par pays (ordre de {@link com.billetplus.events.domain.Pays}) puis par ordre d'affichage. */
    List<Ville> toutes();
}
