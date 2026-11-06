package com.billetplus.events.infrastructure;

import com.billetplus.events.domain.AffectationAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Interface Spring Data — usage interne à infrastructure/ uniquement. */
interface SpringDataAffectationAgentRepository extends JpaRepository<AffectationAgent, AffectationAgent.Cle> {

    @Modifying
    @Query("delete from AffectationAgent a where a.agentId = :agentId")
    void supprimerToutes(@Param("agentId") UUID agentId);

    @Query("select a.agentId from AffectationAgent a where a.eventId = :eventId order by a.affecteLe")
    List<UUID> listerAgents(@Param("eventId") UUID eventId);
}
