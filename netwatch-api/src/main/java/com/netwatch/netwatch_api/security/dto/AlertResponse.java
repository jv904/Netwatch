package com.netwatch.netwatch_api.security.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.netwatch.netwatch_api.security.AlertSeverity;
import com.netwatch.netwatch_api.security.AlertStatus;
import com.netwatch.netwatch_api.security.RuleType;
import com.netwatch.netwatch_api.security.SecurityAlert;

/**
 * @param recommendation o que fazer, conforme a regra que gerou o alerta
 * @param occurrences    quantas vezes o mesmo problema foi detectado enquanto o alerta estava ativo
 */
public record AlertResponse(
        UUID id,
        RuleType rule,
        String ruleTitle,
        AlertSeverity severity,
        AlertStatus status,
        String title,
        String evidence,
        String recommendation,
        UUID deviceId,
        String deviceName,
        String ipAddress,
        int occurrences,
        Instant firstSeenAt,
        Instant lastSeenAt) {

    public static AlertResponse from(SecurityAlert a, Map<UUID, String> deviceNames) {
        return new AlertResponse(a.getId(), a.getRule(), a.getRule().title(), a.getSeverity(), a.getStatus(),
                a.getTitle(), a.getEvidence(), a.getRule().recommendation(), a.getDeviceId(),
                a.getDeviceId() != null ? deviceNames.get(a.getDeviceId()) : null, a.getIpAddress(),
                a.getOccurrences(), a.getFirstSeenAt(), a.getLastSeenAt());
    }
}
