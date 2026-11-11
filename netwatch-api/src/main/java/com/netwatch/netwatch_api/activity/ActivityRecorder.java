package com.netwatch.netwatch_api.activity;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Grava no histórico cada {@link ActivityEvent} publicado. Roda na mesma transação de quem publicou:
 * se a operação for desfeita, o evento também é.
 */
@Component
class ActivityRecorder {

    private static final Logger log = LoggerFactory.getLogger(ActivityRecorder.class);

    private final NetworkEventRepository events;
    private final Clock clock;

    ActivityRecorder(NetworkEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @EventListener
    void on(ActivityEvent event) {
        events.save(new NetworkEvent(event, clock.instant()));
        log.debug("Evento {}: {}", event.type(), event.title());
    }
}
