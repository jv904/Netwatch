package com.netwatch.netwatch_api.traffic;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Apaga fluxos e totais por minuto mais antigos que {@code netwatch.capture.retention-days}. */
@Component
class TrafficRetention {

    private static final Logger log = LoggerFactory.getLogger(TrafficRetention.class);

    private final TrafficFlowRepository flows;
    private final TrafficMinuteStatRepository stats;
    private final CaptureProperties properties;
    private final Clock clock;

    TrafficRetention(TrafficFlowRepository flows, TrafficMinuteStatRepository stats, CaptureProperties properties,
            Clock clock) {
        this.flows = flows;
        this.stats = stats;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    @Transactional
    public void purge() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(Math.max(1, properties.retentionDays())));
        int removedFlows = flows.deleteOlderThan(cutoff);
        int removedStats = stats.deleteOlderThan(cutoff);
        if (removedFlows > 0 || removedStats > 0) {
            log.info("Retenção de tráfego: {} fluxos e {} totais por minuto removidos.", removedFlows, removedStats);
        }
    }
}
