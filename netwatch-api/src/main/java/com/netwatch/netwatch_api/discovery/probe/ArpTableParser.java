package com.netwatch.netwatch_api.discovery.probe;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta a saída da tabela ARP de qualquer sistema/idioma:
 * <ul>
 *   <li>Windows {@code arp -a}: {@code 192.168.0.1   48-8f-5a-ae-4f-06   dinâmico}</li>
 *   <li>Linux {@code /proc/net/arp}: {@code 192.168.0.1  0x1  0x2  48:8f:5a:ae:4f:06  *  eth0}</li>
 *   <li>macOS {@code arp -an}: {@code ? (192.168.0.1) at 48:8f:5a:ae:4f:6 on en0}</li>
 * </ul>
 * Em cada linha, usa o primeiro IPv4 e o primeiro MAC encontrados.
 */
public final class ArpTableParser {

    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(\\d{1,3}(?:\\.\\d{1,3}){3})(?![\\d.])");
    private static final Pattern MAC = Pattern.compile(
            "(?<![0-9a-fA-F:-])([0-9a-fA-F]{1,2}(?:[:-][0-9a-fA-F]{1,2}){5})(?![0-9a-fA-F:-])");

    private ArpTableParser() {
    }

    public static Map<String, String> parse(String output) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (output == null) {
            return entries;
        }
        for (String line : output.split("\\R")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("interface")) {
                continue; // cabeçalho do Windows: "Interface: 192.168.0.5 --- 0x6"
            }
            Matcher ip = IPV4.matcher(line);
            Matcher mac = MAC.matcher(line);
            if (ip.find() && mac.find()) {
                String normalized = normalizeMac(mac.group(1));
                if (isUnicastMac(normalized)) {
                    entries.putIfAbsent(ip.group(1), normalized);
                }
            }
        }
        return entries;
    }

    /** Formato aa:bb:cc:dd:ee:ff, completando octetos de 1 dígito (macOS). */
    static String normalizeMac(String raw) {
        String[] parts = raw.split("[:-]");
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            if (parts[i].length() == 1) {
                sb.append('0');
            }
            sb.append(parts[i].toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** Descarta MAC zerado (entrada incompleta), broadcast e multicast. */
    static boolean isUnicastMac(String mac) {
        if (mac.equals("00:00:00:00:00:00")) {
            return false;
        }
        int firstOctet = Integer.parseInt(mac.substring(0, 2), 16);
        return (firstOctet & 0x01) == 0;
    }
}
