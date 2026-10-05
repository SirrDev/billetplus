package com.billetplus.events.infrastructure;

import com.billetplus.events.application.EventRepository;
import com.billetplus.events.domain.Event;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaEventRepositoryAdapter implements EventRepository {

    private final SpringDataEventRepository springRepo;

    JpaEventRepositoryAdapter(SpringDataEventRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return springRepo.findById(id);
    }

    @Override
    public Event save(Event event) {
        return springRepo.save(event);
    }

    @Override
    public List<Event> findByOrganizerId(UUID organizerId) {
        return springRepo.findByOrganizerId(organizerId);
    }
}
