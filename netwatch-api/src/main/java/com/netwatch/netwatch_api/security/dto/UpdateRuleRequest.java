package com.netwatch.netwatch_api.security.dto;

import java.util.Map;

import com.netwatch.netwatch_api.security.AlertSeverity;

import jakarta.validation.constraints.NotNull;

/**
 * @param params parâmetros da regra (nome → valor); os omitidos voltam ao padrão de fábrica
 */
public record UpdateRuleRequest(
        boolean enabled,
        @NotNull(message = "Informe a gravidade.") AlertSeverity severity,
        Map<String, String> params) {
}
