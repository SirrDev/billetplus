package com.billetplus.events.infrastructure;

import com.billetplus.events.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataVenueRepository extends JpaRepository<Venue, UUID> {
}
