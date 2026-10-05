package com.billetplus.events.application;

import com.billetplus.events.domain.Venue;

import java.util.Optional;
import java.util.UUID;

public interface VenueRepository {

    Optional<Venue> findById(UUID id);

    Venue save(Venue venue);
}
