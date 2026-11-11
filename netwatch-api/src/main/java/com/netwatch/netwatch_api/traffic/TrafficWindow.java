package com.netwatch.netwatch_api.traffic;

import java.time.Duration;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

/** Período das consultas de tráfego e a granularidade do gráfico em cada um. */
public enum TrafficWindow {

    HOUR("1h", Duration.ofHours(1), Duration.ofMinutes(1)),
    DAY("24h", Duration.ofHours(24), Duration.ofMinutes(15)),
    WEEK("7d", Duration.ofDays(7), Duration.ofHours(2));

    private final String code;
    private final Duration length;
    private final Duration bucket;

    TrafficWindow(String code, Duration length, Duration bucket) {
        this.code = code;
        this.length = length;
        this.bucket = bucket;
    }

    public static TrafficWindow parse(String value) {
        if (value == null || value.isBlank()) {
            return HOUR;
        }
        for (TrafficWindow w : values()) {
            if (w.code.equalsIgnoreCase(value.trim())) {
                return w;
            }
        }
        throw new InvalidInputException("Período inválido: " + value + ". Use 1h, 24h ou 7d.");
    }

    public String code() { return code; }
    public Duration length() { return length; }
    public Duration bucket() { return bucket; }
}
