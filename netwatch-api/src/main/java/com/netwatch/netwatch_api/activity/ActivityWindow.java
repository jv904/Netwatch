package com.netwatch.netwatch_api.activity;

import java.time.Duration;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

/** Período das análises de atividade e a granularidade da linha do tempo. */
public enum ActivityWindow {

    DAY("24h", Duration.ofHours(24), Duration.ofHours(1)),
    WEEK("7d", Duration.ofDays(7), Duration.ofHours(6)),
    MONTH("30d", Duration.ofDays(30), Duration.ofDays(1));

    private final String code;
    private final Duration length;
    private final Duration bucket;

    ActivityWindow(String code, Duration length, Duration bucket) {
        this.code = code;
        this.length = length;
        this.bucket = bucket;
    }

    public static ActivityWindow parse(String value) {
        if (value == null || value.isBlank()) {
            return WEEK;
        }
        for (ActivityWindow w : values()) {
            if (w.code.equalsIgnoreCase(value.trim())) {
                return w;
            }
        }
        throw new InvalidInputException("Período inválido: " + value + ". Use 24h, 7d ou 30d.");
    }

    public String code() { return code; }
    public Duration length() { return length; }
    public Duration bucket() { return bucket; }
}
