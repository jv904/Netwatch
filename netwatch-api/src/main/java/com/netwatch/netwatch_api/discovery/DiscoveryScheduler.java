package com.netwatch.netwatch_api.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.common.exception.ConflictException;

/** Descoberta periódica, ativada por {@code netwatch.discovery.schedule.enabled=true}. */
@Component
@ConditionalOnProperty(name = "netwatch.discovery.schedule.enabled", havingValue = "true")
class DiscoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryScheduler.class);

    private final DiscoveryService service;

    DiscoveryScheduler(DiscoveryService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${netwatch.discovery.schedule.interval:15m}")
    void run() {
        try {
            service.start(null, DiscoveryTrigger.SCHEDULED);
        } catch (ConflictException e) {
            log.debug("Descoberta periódica ignorada: já existe uma em andamento.");
        } catch (RuntimeException e) {
            log.warn("Descoberta periódica não iniciada: {}", e.getMessage());
        }
    }
}
