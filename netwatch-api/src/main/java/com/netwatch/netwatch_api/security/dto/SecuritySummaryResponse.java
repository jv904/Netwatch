package com.netwatch.netwatch_api.security.dto;

import java.util.List;

import com.netwatch.netwatch_api.security.RuleType;

/**
 * Situação de segurança: alertas ativos (abertos ou reconhecidos) por gravidade e por regra.
 */
public record SecuritySummaryResponse(
        long activeAlerts,
        long critical,
        long high,
        long medium,
        long low,
        long rulesEnabled,
        long rulesTotal,
        List<RuleCount> byRule,
        List<AlertResponse> recent) {

    public record RuleCount(RuleType rule, String title, long activeAlerts) {
    }
}
