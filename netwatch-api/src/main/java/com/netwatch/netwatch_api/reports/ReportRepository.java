package com.netwatch.netwatch_api.reports;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByType(ReportType type, Pageable pageable);

    boolean existsByTypeAndTriggerAndPeriodStartAndPeriodEnd(ReportType type, Report.Trigger trigger,
            Instant periodStart, Instant periodEnd);
}
