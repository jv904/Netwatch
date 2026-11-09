package com.netwatch.netwatch_api.device.dto;

/** Indicadores de dispositivos para o Dashboard. */
public record DeviceSummary(long total, long active, long inactive, long unrecognized) {
}
