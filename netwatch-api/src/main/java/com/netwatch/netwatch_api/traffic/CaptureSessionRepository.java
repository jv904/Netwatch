package com.netwatch.netwatch_api.traffic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureSessionRepository extends JpaRepository<CaptureSession, UUID> {

    List<CaptureSession> findByStatus(CaptureSessionStatus status);

    Optional<CaptureSession> findFirstByOrderByStartedAtDesc();

    /** Sessões iniciadas no período [from, to). */
    List<CaptureSession> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAt(java.time.Instant from,
            java.time.Instant to);
}
