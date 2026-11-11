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

/**
 * Um alerta gerado por uma regra. Ocorrências repetidas (mesma {@code dedupKey}) enquanto o alerta está
 * ativo somam em {@code occurrences} em vez de criar alertas novos.
 */
@Entity
@Table(name = "security_alerts")
public class SecurityAlert {

    private static final int MAX_TEXT = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "rule_code", nullable = false, length = 40)
    private RuleType rule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = MAX_TEXT)
    private String evidence;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "dedup_key", nullable = false, length = 200)
    private String dedupKey;

    @Column(nullable = false)
    private int occurrences;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    /** Observação de quem resolveu ou descartou. */
    @Column(name = "resolution_note", length = MAX_TEXT)
    private String resolutionNote;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dismiss_reason", length = 30)
    private DismissReason dismissReason;

    /** Descartado com "não alertar novamente": novas ocorrências só somam aqui. */
    @Column(nullable = false)
    private boolean suppressed;

    protected SecurityAlert() {
    }

    SecurityAlert(RuleType rule, AlertSeverity severity, String title, String evidence, UUID deviceId,
            String ipAddress, String dedupKey, Instant at) {
        this.rule = rule;
        this.severity = severity;
        this.status = AlertStatus.OPEN;
        this.title = truncate(title, 300);
        this.evidence = truncate(evidence, MAX_TEXT);
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        this.dedupKey = truncate(dedupKey, 200);
        this.occurrences = 1;
        this.firstSeenAt = at;
        this.lastSeenAt = at;
        this.statusChangedAt = at;
    }

    // ------------------------------------------------------------------ ciclo de vida

    /** Alguém viu o alerta e está cuidando dele. */
    AlertStatus acknowledge(Instant at) {
        requireStatus("reconhecer", AlertStatus.OPEN);
        return changeTo(AlertStatus.ACKNOWLEDGED, at);
    }

    /** O problema foi corrigido. */
    AlertStatus resolve(Instant at, String note) {
        requireStatus("resolver", AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED);
        resolutionNote = truncate(blankToNull(note), MAX_TEXT);
        return changeTo(AlertStatus.RESOLVED, at);
    }

    /**
     * Não é um problema (ou é um risco aceito).
     *
     * @param suppress novas ocorrências do mesmo problema não abrem alerta novo
     */
    AlertStatus dismiss(Instant at, DismissReason reason, String note, boolean suppress) {
        requireStatus("descartar", AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED);
        if (reason == null) {
            throw new com.netwatch.netwatch_api.common.exception.InvalidInputException(
                    "Informe o motivo do descarte.");
        }
        dismissReason = reason;
        resolutionNote = truncate(blankToNull(note), MAX_TEXT);
        suppressed = suppress;
        return changeTo(AlertStatus.DISMISSED, at);
    }

    /** Volta a ficar aberto (o problema reapareceu ou foi fechado por engano). */
    AlertStatus reopen(Instant at) {
        requireStatus("reabrir", AlertStatus.RESOLVED, AlertStatus.DISMISSED);
        dismissReason = null;
        resolutionNote = null;
        suppressed = false;
        return changeTo(AlertStatus.OPEN, at);
    }

    private AlertStatus changeTo(AlertStatus next, Instant at) {
        AlertStatus previous = status;
        status = next;
        statusChangedAt = at;
        return previous;
    }

    private void requireStatus(String action, AlertStatus... allowed) {
        for (AlertStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new com.netwatch.netwatch_api.common.exception.ConflictException(
                "Não é possível " + action + " um alerta " + describe(status) + ".");
    }

    private static String describe(AlertStatus status) {
        return switch (status) {
            case OPEN -> "aberto";
            case ACKNOWLEDGED -> "já reconhecido";
            case RESOLVED -> "resolvido";
            case DISMISSED -> "descartado";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Nova ocorrência do mesmo problema: atualiza a evidência e a gravidade (se subiu). */
    void repeat(AlertSeverity newSeverity, String newEvidence, Instant at) {
        occurrences++;
        lastSeenAt = at;
        if (newEvidence != null) {
            evidence = truncate(newEvidence, MAX_TEXT);
        }
        if (newSeverity.compareTo(severity) > 0) {
            severity = newSeverity;
        }
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    public UUID getId() { return id; }
    public RuleType getRule() { return rule; }
    public AlertSeverity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getEvidence() { return evidence; }
    public UUID getDeviceId() { return deviceId; }
    public String getIpAddress() { return ipAddress; }
    public String getDedupKey() { return dedupKey; }
    public int getOccurrences() { return occurrences; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public DismissReason getDismissReason() { return dismissReason; }
    public boolean isSuppressed() { return suppressed; }
}
