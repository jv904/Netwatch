package com.netwatch.netwatch_api.traffic.connections;

import java.util.ArrayList;
import java.util.List;

import com.netwatch.netwatch_api.discovery.Ipv4Network;
import com.netwatch.netwatch_api.traffic.TrafficProtocol;

/**
 * Interpreta a saída de {@code netstat -ano} do Windows. O cabeçalho vem traduzido conforme o idioma
 * do sistema, mas as linhas de dados não ({@code TCP 192.168.0.5:50123 1.2.3.4:443 ESTABLISHED 1234}),
 * então só elas são lidas. Endereços IPv6 são ignorados.
 */
public final class NetstatParser {

    private NetstatParser() {
    }

    public static List<ConnectionEntry> parse(String output) {
        List<ConnectionEntry> entries = new ArrayList<>();
        if (output == null) {
            return entries;
        }
        for (String raw : output.split("\\R")) {
            String[] t = raw.trim().split("\\s+");
            if (t.length < 4) {
                continue;
            }
            TrafficProtocol protocol = switch (t[0].toUpperCase()) {
                case "TCP" -> TrafficProtocol.TCP;
                case "UDP" -> TrafficProtocol.UDP;
                default -> null;
            };
            if (protocol == null) {
                continue;
            }
            boolean tcp = protocol == TrafficProtocol.TCP;
            if (tcp && t.length < 5) {
                continue;
            }
            Endpoint local = Endpoint.parse(t[1]);
            if (local == null) {
                continue;
            }
            Endpoint remote = Endpoint.parse(t[2]);
            String state = tcp ? t[3] : null;
            Integer pid = parseInt(t[tcp ? 4 : 3]);
            boolean noRemote = remote == null || remote.ip().equals("0.0.0.0");
            entries.add(new ConnectionEntry(protocol, local.ip(), local.port(),
                    noRemote ? null : remote.ip(), noRemote ? null : remote.port(), state, pid, null));
        }
        return entries;
    }

    private record Endpoint(String ip, int port) {

        /** "192.168.0.5:443" → Endpoint; "[::]:135", "*:*" e similares → nulo. */
        static Endpoint parse(String value) {
            int colon = value.lastIndexOf(':');
            if (colon <= 0 || value.startsWith("[")) {
                return null;
            }
            String ip = value.substring(0, colon);
            Integer port = parseInt(value.substring(colon + 1));
            if (port == null || Ipv4Network.toInt(ip).isEmpty()) {
                return null;
            }
            return new Endpoint(ip, port);
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
