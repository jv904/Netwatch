package com.netwatch.netwatch_api.ports;

import java.util.List;
import java.util.TreeSet;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

/** Interpreta listas de portas como {@code "22,80,8000-8100"}. */
public final class PortSpec {

    private PortSpec() {
    }

    public static List<Integer> parse(String spec, int maxPorts) {
        if (spec == null || spec.isBlank()) {
            throw new InvalidInputException("Informe as portas a verificar (ex.: 22,80,8000-8100).");
        }
        TreeSet<Integer> ports = new TreeSet<>();
        for (String raw : spec.split(",")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            int dash = part.indexOf('-');
            int from = parsePort(dash < 0 ? part : part.substring(0, dash), spec);
            int to = dash < 0 ? from : parsePort(part.substring(dash + 1), spec);
            if (to < from) {
                throw new InvalidInputException("Intervalo de portas inválido: " + part);
            }
            if ((long) ports.size() + (to - from + 1) > maxPorts) {
                throw new InvalidInputException("No máximo " + maxPorts + " portas podem ser verificadas por vez.");
            }
            for (int p = from; p <= to; p++) {
                ports.add(p);
            }
        }
        if (ports.isEmpty()) {
            throw new InvalidInputException("Informe ao menos uma porta.");
        }
        return List.copyOf(ports);
    }

    private static int parsePort(String value, String spec) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new InvalidInputException("Porta fora do intervalo 1–65535: " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Lista de portas inválida: " + spec);
        }
    }
}
