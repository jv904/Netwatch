package com.netwatch.netwatch_api.security;

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

/** Item da linha do tempo de um alerta: um comentário ou uma mudança de situação. */
@Entity
@Table(name = "alert_notes")
public class AlertNote {

    public enum Kind {
        COMMENT, STATUS
    }

    private static final int MAX_TEXT = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20)
    private AlertStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", length = 20)
    private AlertStatus toStatus;

    @Column(length = MAX_TEXT)
    private String text;

    protected AlertNote() {
    }

    private AlertNote(UUID alertId, Instant createdAt, Kind kind, AlertStatus from, AlertStatus to, String text) {
        this.alertId = alertId;
        this.createdAt = createdAt;
        this.kind = kind;
        this.fromStatus = from;
        this.toStatus = to;
        this.text = text != null && text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    static AlertNote comment(UUID alertId, Instant at, String text) {
        return new AlertNote(alertId, at, Kind.COMMENT, null, null, text);
    }

    static AlertNote statusChange(UUID alertId, Instant at, AlertStatus from, AlertStatus to, String text) {
        return new AlertNote(alertId, at, Kind.STATUS, from, to, text);
    }

    public UUID getId() { return id; }
    public UUID getAlertId() { return alertId; }
    public Instant getCreatedAt() { return createdAt; }
    public Kind getKind() { return kind; }
    public AlertStatus getFromStatus() { return fromStatus; }
    public AlertStatus getToStatus() { return toStatus; }
    public String getText() { return text; }
}
