package com.netwatch.netwatch_api.reports;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

/**
 * Período de um relatório: [from, to).
 *
 * @param label período em texto, no fuso local
 */
public record ReportPeriod(Instant from, Instant to, String label) {

    /** Períodos prontos. As semanas começam na segunda-feira; mês e semana anteriores são completos. */
    public enum Preset {
        LAST_24H, LAST_7D, LAST_30D, PREVIOUS_WEEK, PREVIOUS_MONTH, CUSTOM
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Limite de um relatório personalizado. */
    private static final Duration MAX_LENGTH = Duration.ofDays(366);

    /**
     * @param from início (só para CUSTOM; data local, incluída)
     * @param to   fim (só para CUSTOM; data local, incluída)
     */
    public static ReportPeriod resolve(Preset preset, LocalDate from, LocalDate to, Instant now, ZoneId zone) {
        Preset p = preset != null ? preset : Preset.LAST_7D;
        ZonedDateTime local = now.atZone(zone);
        return switch (p) {
            case LAST_24H -> rolling(now.minus(Duration.ofHours(24)), now, zone);
            case LAST_7D -> rolling(now.minus(Duration.ofDays(7)), now, zone);
            case LAST_30D -> rolling(now.minus(Duration.ofDays(30)), now, zone);
            case PREVIOUS_WEEK -> {
                LocalDate monday = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield days(monday.minusWeeks(1), monday.minusDays(1), zone);
            }
            case PREVIOUS_MONTH -> {
                LocalDate first = local.toLocalDate().withDayOfMonth(1).minusMonths(1);
                yield days(first, first.with(TemporalAdjusters.lastDayOfMonth()), zone);
            }
            case CUSTOM -> {
                if (from == null || to == null) {
                    throw new InvalidInputException("Informe as datas inicial e final do período.");
                }
                if (to.isBefore(from)) {
                    throw new InvalidInputException("A data final deve ser igual ou posterior à inicial.");
                }
                ReportPeriod period = days(from, to, zone);
                if (Duration.between(period.from(), period.to()).compareTo(MAX_LENGTH) > 0) {
                    throw new InvalidInputException("O período máximo de um relatório é de 1 ano.");
                }
                yield period;
            }
        };
    }

    /** Dias inteiros, de 00:00 de {@code first} até 00:00 do dia seguinte a {@code last}. */
    static ReportPeriod days(LocalDate first, LocalDate last, ZoneId zone) {
        Instant from = first.atStartOfDay(zone).toInstant();
        Instant to = last.plusDays(1).atStartOfDay(zone).toInstant();
        String label = first.equals(last) ? DAY.format(first) : DAY.format(first) + " a " + DAY.format(last);
        return new ReportPeriod(from, to, label);
    }

    private static ReportPeriod rolling(Instant from, Instant to, ZoneId zone) {
        return new ReportPeriod(from, to, DAY_TIME.format(from.atZone(zone)) + " a " + DAY_TIME.format(to.atZone(zone)));
    }

    public boolean contains(Instant at) {
        return at != null && !at.isBefore(from) && at.isBefore(to);
    }
}
