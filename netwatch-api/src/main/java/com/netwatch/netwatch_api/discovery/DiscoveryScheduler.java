package com.netwatch.netwatch_api.discovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.common.exception.ConflictException;
import com.netwatch.netwatch_api.settings.SettingKey;
import com.netwatch.netwatch_api.settings.SettingsService;

/**
 * Descoberta periódica. Confere a cada minuto se está ligada e se já passou o intervalo desde a última
 * descoberta (manual ou automática); assim, ligar, desligar ou mudar o intervalo na tela vale na hora.
 */
@Component
class DiscoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryScheduler.class);

    private final DiscoveryService service;
    private final DiscoveryRunRepository runs;
    private final SettingsService settings;
    private final Clock clock;

    DiscoveryScheduler(DiscoveryService service, DiscoveryRunRepository runs, SettingsService settings, Clock clock) {
        this.service = service;
        this.runs = runs;
        this.settings = settings;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1M")
    void tick() {
        if (!settings.bool(SettingKey.DISCOVERY_SCHEDULE_ENABLED)) {
            return;
        }
        Duration interval = settings.minutes(SettingKey.DISCOVERY_SCHEDULE_INTERVAL);
        Instant last = runs.findFirstByOrderByStartedAtDesc().map(DiscoveryRun::getStartedAt).orElse(null);
        if (last != null && Duration.between(last, clock.instant()).compareTo(interval) < 0) {
            return;
        }
        try {
            service.start(null, DiscoveryTrigger.SCHEDULED);
        } catch (ConflictException e) {
            log.debug("Descoberta periódica ignorada: já existe uma em andamento.");
        } catch (RuntimeException e) {
            log.warn("Descoberta periódica não iniciada: {}", e.getMessage());
        }
    }
}
