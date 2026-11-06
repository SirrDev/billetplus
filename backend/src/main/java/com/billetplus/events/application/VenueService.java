package com.billetplus.events.application;

import com.billetplus.events.domain.Venue;
import com.billetplus.events.domain.Ville;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final VilleRepository villeRepository;

    public VenueService(VenueRepository venueRepository, VilleRepository villeRepository) {
        this.venueRepository = venueRepository;
        this.villeRepository = villeRepository;
    }

    /**
     * @param villeCode code d'une ville de la liste fermée (ADR 0011)
     * @throws IllegalArgumentException (400) si la ville est inconnue, ou si le nom ou l'adresse manque
     */
    @Transactional
    public Venue create(String nom, String adresse, Double latitude, Double longitude, String villeCode) {
        Ville ville = villeRepository.findByCode(villeCode)
            .orElseThrow(() -> new IllegalArgumentException("Ville inconnue"));
        return venueRepository.save(new Venue(nom, adresse, latitude, longitude, ville));
    }

    /** Villes de la liste fermée, par pays puis par ordre d'affichage (route publique GET /api/villes). */
    @Transactional(readOnly = true)
    public List<Ville> villes() {
        return villeRepository.toutes();
    }

    @Transactional(readOnly = true)
    public Venue get(UUID id) {
        return venueRepository.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("Lieu introuvable : " + id));
    }
}
