package com.netwatch.netwatch_api.activity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.netwatch.netwatch_api.activity.EventCategory;
import com.netwatch.netwatch_api.activity.EventSeverity;
import com.netwatch.netwatch_api.activity.EventType;

/**
 * Análise das atividades no período.
 *
 * @param series     eventos por intervalo ({@code bucketSeconds}), em ordem cronológica e sem lacunas
 * @param byHour     distribuição por hora do dia (0–23) no fuso {@code timeZone}
 * @param topDevices dispositivos com mais eventos
 */
public record ActivitySummaryResponse(
        String window,
        Instant since,
        Instant until,
        long bucketSeconds,
        String timeZone,
        Totals totals,
        List<Point> series,
        List<TypeCount> byType,
        List<HourCount> byHour,
        List<DeviceCount> topDevices) {

    public record Totals(long total, long warnings, long notices, long info, long newDevices, long portsOpened,
            long newServices) {
    }

    public record Point(Instant at, long total, long warnings) {
    }

    public record TypeCount(EventType type, EventCategory category, EventSeverity severity, long count) {
    }

    public record HourCount(int hour, long count) {
    }

    public record DeviceCount(UUID deviceId, String name, String ipAddress, long count, long warnings) {
    }
}
