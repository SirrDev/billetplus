package com.billetplus.events.infrastructure;

import com.billetplus.events.domain.Ville;
import org.springframework.data.jpa.repository.JpaRepository;

/** Interface Spring Data — usage interne à infrastructure/ uniquement. */
interface SpringDataVilleRepository extends JpaRepository<Ville, String> {
}
