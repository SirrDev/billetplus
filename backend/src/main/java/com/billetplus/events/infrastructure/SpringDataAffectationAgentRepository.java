package com.billetplus.events.infrastructure;

import com.billetplus.events.domain.AffectationAgent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Interface Spring Data — usage interne à infrastructure/ uniquement. */
interface SpringDataAffectationAgentRepository extends JpaRepository<AffectationAgent, AffectationAgent.Cle> {
}
