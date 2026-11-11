package com.netwatch.netwatch_api.reports;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação em português para o conteúdo dos relatórios. */
final class Fmt {

    static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private final ZoneId zone;

    Fmt(ZoneId zone) {
        this.zone = zone;
    }

    String dateTime(Instant at) {
        return at == null ? "—" : DATE_TIME.format(at.atZone(zone));
    }

    String date(Instant at) {
        return at == null ? "—" : DATE.format(at.atZone(zone));
    }

    String day(Instant at) {
        return DAY.format(at.atZone(zone));
    }

    static String num(long value) {
        return NumberFormat.getIntegerInstance(PT_BR).format(value);
    }

    static String bytes(long bytes) {
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        NumberFormat nf = NumberFormat.getNumberInstance(PT_BR);
        nf.setMaximumFractionDigits(unit == 0 || value >= 100 ? 0 : 1);
        return nf.format(value) + " " + UNITS[unit];
    }

    static String duration(Duration d) {
        if (d == null) {
            return "—";
        }
        long minutes = d.toMinutes();
        if (minutes < 1) {
            return d.toSeconds() + " s";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = d.toHours();
        if (hours < 48) {
            return hours + " h " + (minutes % 60) + " min";
        }
        return d.toDays() + " dias " + (hours % 24) + " h";
    }

    static String percent(long part, long total) {
        if (total == 0) {
            return "0%";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(PT_BR);
        nf.setMaximumFractionDigits(1);
        return nf.format(part * 100.0 / total) + "%";
    }

    static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
