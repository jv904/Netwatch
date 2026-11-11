package com.netwatch.netwatch_api.activity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um registro do histórico de atividades da rede. */
@Entity
@Table(name = "network_events")
public class NetworkEvent {

    private static final int MAX_TITLE = 300;
    private static final int MAX_DETAILS = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EventSeverity severity;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(length = MAX_DETAILS)
    private String details;

    @Column(name = "reference_id")
    private UUID referenceId;

    protected NetworkEvent() {
    }

    NetworkEvent(ActivityEvent event, Instant occurredAt) {
        this.occurredAt = occurredAt;
        this.type = event.type();
        this.category = event.type().category();
        this.severity = event.type().severity();
        this.deviceId = event.deviceId();
        this.ipAddress = event.ipAddress();
        this.title = truncate(event.title(), MAX_TITLE);
        this.details = truncate(event.details(), MAX_DETAILS);
        this.referenceId = event.referenceId();
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public EventType getType() { return type; }
    public EventCategory getCategory() { return category; }
    public EventSeverity getSeverity() { return severity; }
    public UUID getDeviceId() { return deviceId; }
    public String getIpAddress() { return ipAddress; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public UUID getReferenceId() { return referenceId; }
}
