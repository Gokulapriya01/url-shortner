package com.urlshortener.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.urlshortener.domain.entity.Gate;

@Repository
public interface GateRepository extends JpaRepository<Gate, UUID> {

    /** Returns all gates belonging to the session. */
    List<Gate> findBySessionId(UUID sessionId);

    /** Returns session gates ordered by phase. */
    List<Gate> findBySessionIdOrderByPhase(UUID sessionId);

    /** Returns the session gate for the requested phase, if present. */
    Optional<Gate> findBySessionIdAndPhase(UUID sessionId, int phase);

    /** Returns the gate only when it belongs to the supplied session. */
    Optional<Gate> findBySessionIdAndId(UUID sessionId, UUID gateId);

    /** Returns all unapproved gates belonging to the session. */
    List<Gate> findBySessionIdAndApprovedFalse(UUID sessionId);

    /** Returns all unapproved gates across sessions. */
    List<Gate> findByApprovedFalse();

    /** Returns whether the session phase has an unapproved gate. */
    boolean existsBySessionIdAndPhaseAndApprovedFalse(UUID sessionId, int phase);
}
