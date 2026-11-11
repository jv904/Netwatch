package com.netwatch.netwatch_api.activity;

import static com.netwatch.netwatch_api.activity.EventCategory.DEVICE;
import static com.netwatch.netwatch_api.activity.EventCategory.DISCOVERY;
import static com.netwatch.netwatch_api.activity.EventCategory.PORT;
import static com.netwatch.netwatch_api.activity.EventCategory.TRAFFIC;
import static com.netwatch.netwatch_api.activity.EventSeverity.INFO;
import static com.netwatch.netwatch_api.activity.EventSeverity.NOTICE;
import static com.netwatch.netwatch_api.activity.EventSeverity.WARNING;

/** O que aconteceu. Cada tipo tem categoria e importância fixas. */
public enum EventType {

    /** Equipamento visto pela primeira vez (entra como desconhecido). */
    DEVICE_DISCOVERED(DEVICE, WARNING),
    /** Equipamento conhecido apareceu com outro IP. */
    DEVICE_IP_CHANGED(DEVICE, NOTICE),
    /** Equipamento inativo voltou a ser visto. */
    DEVICE_RETURNED(DEVICE, INFO),
    /** Equipamento não respondeu na descoberta. */
    DEVICE_INACTIVE(DEVICE, INFO),
    DEVICE_CREATED(DEVICE, INFO),
    DEVICE_RECOGNIZED(DEVICE, INFO),
    DEVICE_UNRECOGNIZED(DEVICE, NOTICE),
    DEVICE_DELETED(DEVICE, INFO),

    /** Porta que estava fechada/sem resposta passou a aceitar conexões. */
    PORT_OPENED(PORT, WARNING),
    /** Porta que aceitava conexões deixou de aceitar. */
    PORT_CLOSED(PORT, NOTICE),
    PORT_SCAN_COMPLETED(PORT, INFO),
    PORT_SCAN_FAILED(PORT, NOTICE),

    DISCOVERY_COMPLETED(DISCOVERY, INFO),
    DISCOVERY_FAILED(DISCOVERY, NOTICE),

    CAPTURE_STARTED(TRAFFIC, INFO),
    CAPTURE_FINISHED(TRAFFIC, INFO),
    CAPTURE_FAILED(TRAFFIC, NOTICE),
    /** Um dispositivo da rede foi visto oferecendo um serviço pela primeira vez no tráfego. */
    TRAFFIC_NEW_SERVICE(TRAFFIC, NOTICE);

    private final EventCategory category;
    private final EventSeverity severity;

    EventType(EventCategory category, EventSeverity severity) {
        this.category = category;
        this.severity = severity;
    }

    public EventCategory category() {
        return category;
    }

    public EventSeverity severity() {
        return severity;
    }
}
