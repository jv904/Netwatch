package com.netwatch.netwatch_api.discovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveryRunRepository extends JpaRepository<DiscoveryRun, UUID> {

    List<DiscoveryRun> findByStatus(DiscoveryRunStatus status);

    Optional<DiscoveryRun> findFirstByStatusOrderByStartedAtDesc(DiscoveryRunStatus status);

    Optional<DiscoveryRun> findFirstByOrderByStartedAtDesc();

    List<DiscoveryRun> findTop20ByStatusOrderByStartedAtDesc(DiscoveryRunStatus status);

    /** Execuções iniciadas no período [from, to). */
    List<DiscoveryRun> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAt(java.time.Instant from,
            java.time.Instant to);
}
