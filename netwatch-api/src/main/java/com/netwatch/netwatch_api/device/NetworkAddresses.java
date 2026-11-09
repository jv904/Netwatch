package com.netwatch.netwatch_api.device;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

/**
 * Validação e normalização de endereços IP e MAC.
 * Nenhum método aqui faz consulta DNS.
 */
final class NetworkAddresses {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final Pattern MAC = Pattern.compile("^[0-9a-f]{12}$");

    private NetworkAddresses() {
    }

    /** Retorna o IP normalizado ou lança {@link InvalidInputException}. */
    static String normalizeIp(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("O endereço IP é obrigatório.");
        }
        String ip = raw.trim();
        if (IPV4.matcher(ip).matches()) {
            return ip;
        }
        // Só tenta IPv6 se for um literal (contém ':' e apenas caracteres hex) — evita resolução DNS.
        if (ip.contains(":") && IPV6_CHARS.matcher(ip).matches()) {
            try {
                return InetAddress.getByName(ip).getHostAddress().toLowerCase(Locale.ROOT);
            } catch (UnknownHostException ignored) {
                // cai no erro abaixo
            }
        }
        throw new InvalidInputException("Endereço IP inválido: " + raw);
    }

    /**
     * Normaliza o MAC para o formato {@code aa:bb:cc:dd:ee:ff}.
     * Aceita separadores ':', '-', '.' ou nenhum. Retorna {@code null} se vazio.
     */
    static String normalizeMac(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String hex = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[:\\-.]", "");
        if (!MAC.matcher(hex).matches()) {
            throw new InvalidInputException("Endereço MAC inválido: " + raw);
        }
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(hex, i, i + 2);
        }
        return sb.toString();
    }
}
