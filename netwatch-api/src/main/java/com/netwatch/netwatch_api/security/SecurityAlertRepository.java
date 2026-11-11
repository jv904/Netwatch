package com.netwatch.netwatch_api.security;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, UUID>,
        JpaSpecificationExecutor<SecurityAlert> {

    Optional<SecurityAlert> findFirstByDedupKeyAndStatusIn(String dedupKey, Collection<AlertStatus> statuses);

    /** Alerta descartado com "não alertar novamente" para o mesmo problema. */
    Optional<SecurityAlert> findFirstByDedupKeyAndSuppressedTrue(String dedupKey);

    List<SecurityAlert> findByDeviceIdAndStatusIn(UUID deviceId, Collection<AlertStatus> statuses);

    Optional<SecurityAlert> findFirstByOrderByFirstSeenAtDesc();

    /** Alertas abertos no período [from, to). */
    List<SecurityAlert> findByFirstSeenAtGreaterThanEqualAndFirstSeenAtLessThanOrderByFirstSeenAtDesc(
            java.time.Instant from, java.time.Instant to);

    List<SecurityAlert> findByStatusIn(Collection<AlertStatus> statuses);

    @Query("select a.status as status, count(a) as total from SecurityAlert a group by a.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        AlertStatus getStatus();
        long getTotal();
    }

    List<SecurityAlert> findByStatusInOrderByLastSeenAtDesc(Collection<AlertStatus> statuses, Limit limit);

    @Query("""
            select a.rule as rule, a.severity as severity, count(a) as total
            from SecurityAlert a
            where a.status in :statuses
            group by a.rule, a.severity
            """)
    List<RuleCount> countActive(Collection<AlertStatus> statuses);

    interface RuleCount {
        RuleType getRule();
        AlertSeverity getSeverity();
        long getTotal();
    }
}
