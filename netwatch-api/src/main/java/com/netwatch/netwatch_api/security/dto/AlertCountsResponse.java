package com.netwatch.netwatch_api.security.dto;

import java.time.Instant;

/**
 * Contadores de alertas.
 *
 * @param active       abertos + reconhecidos
 * @param lastRaisedAt quando o alerta mais recente foi aberto (para avisar o usuário de alerta novo)
 */
public record AlertCountsResponse(
        long active,
        long open,
        long acknowledged,
        long resolved,
        long dismissed,
        long critical,
        long high,
        long medium,
        long low,
        Instant lastRaisedAt) {
}
