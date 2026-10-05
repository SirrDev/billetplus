package com.billetplus.events.infrastructure;

import com.billetplus.events.application.VenueRepository;
import com.billetplus.events.domain.Venue;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaVenueRepositoryAdapter implements VenueRepository {

    private final SpringDataVenueRepository springRepo;

    JpaVenueRepositoryAdapter(SpringDataVenueRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<Venue> findById(UUID id) {
        return springRepo.findById(id);
    }

    @Override
    public Venue save(Venue venue) {
        return springRepo.save(venue);
    }
}
