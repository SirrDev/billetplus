package com.billetplus.events.application;

import com.billetplus.events.domain.Event;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

    Optional<Event> findById(UUID id);

    Event save(Event event);

    java.util.List<Event> findByOrganizerId(UUID organizerId);
}
