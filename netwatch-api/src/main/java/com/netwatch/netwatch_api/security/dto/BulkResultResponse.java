package com.netwatch.netwatch_api.security.dto;

/**
 * @param changed alertas que mudaram de situação
 * @param skipped alertas ignorados (inexistentes ou em situação que não permite a ação)
 */
public record BulkResultResponse(int changed, int skipped) {
}
