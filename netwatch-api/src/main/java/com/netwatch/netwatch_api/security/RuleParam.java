package com.netwatch.netwatch_api.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.discovery.Ipv4Network;

/**
 * Um parâmetro ajustável de regra.
 *
 * @param name         chave (ex.: {@code ports})
 * @param label        rótulo em português
 * @param description  explicação para a tela
 * @param type         formato do valor
 * @param defaultValue valor de fábrica
 */
public record RuleParam(String name, String label, String description, Type type, String defaultValue) {

    public enum Type {
        /** Número inteiro positivo. */
        INTEGER,
        /** Hora do dia (0–23). */
        HOUR,
        /** Lista de portas separadas por vírgula. */
        PORT_LIST,
        /** Lista de IPv4 separados por vírgula (pode ficar vazia). */
        IP_LIST
    }

    /** Valida e normaliza o valor informado (ex.: remove espaços e repetições). */
    public String normalize(String value) {
        String v = value == null ? "" : value.trim();
        return switch (type) {
            case INTEGER -> String.valueOf(parseInt(v, 1, 100_000));
            case HOUR -> String.valueOf(parseInt(v, 0, 23));
            case PORT_LIST -> {
                Set<Integer> ports = parsePorts(v);
                if (ports.isEmpty()) {
                    throw invalid("informe ao menos uma porta");
                }
                yield ports.stream().map(String::valueOf).collect(Collectors.joining(","));
            }
            case IP_LIST -> {
                Set<String> ips = new LinkedHashSet<>();
                for (String ip : split(v)) {
                    if (Ipv4Network.toInt(ip).isEmpty()) {
                        throw invalid("IP inválido: " + ip);
                    }
                    ips.add(ip);
                }
                yield String.join(",", ips);
            }
        };
    }

    private int parseInt(String v, int min, int max) {
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                throw invalid("use um valor entre " + min + " e " + max);
            }
            return n;
        } catch (NumberFormatException e) {
            throw invalid("informe um número");
        }
    }

    static Set<Integer> parsePorts(String v) {
        Set<Integer> ports = new java.util.TreeSet<>();
        for (String p : split(v)) {
            try {
                int port = Integer.parseInt(p);
                if (port < 1 || port > 65535) {
                    throw new InvalidInputException("Porta inválida: " + p + ".");
                }
                ports.add(port);
            } catch (NumberFormatException e) {
                throw new InvalidInputException("Porta inválida: " + p + ".");
            }
        }
        return ports;
    }

    private static String[] split(String v) {
        return v.isBlank() ? new String[0]
                : Arrays.stream(v.split("[,;\\s]+")).filter(s -> !s.isBlank()).toArray(String[]::new);
    }

    private InvalidInputException invalid(String reason) {
        return new InvalidInputException("Parâmetro \"" + label + "\": " + reason + ".");
    }
}
