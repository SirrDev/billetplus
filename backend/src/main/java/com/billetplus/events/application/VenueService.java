package com.billetplus.events.application;

import com.billetplus.events.domain.Venue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional
    public Venue create(String nom, String adresse, Double latitude, Double longitude) {
        Venue venue = new Venue(nom, adresse, latitude, longitude);
        return venueRepository.save(venue);
    }

    @Transactional(readOnly = true)
    public Venue get(UUID id) {
        return venueRepository.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("Lieu introuvable : " + id));
    }
}
