package com.netwatch.netwatch_api.traffic;

import java.util.Map;

import com.netwatch.netwatch_api.ports.KnownServices;

/** Nome do serviço associado a uma porta, conforme o protocolo. */
public final class TrafficServices {

    private static final Map<Integer, String> UDP = Map.ofEntries(
            Map.entry(53, "DNS"),
            Map.entry(67, "DHCP"),
            Map.entry(68, "DHCP"),
            Map.entry(69, "TFTP"),
            Map.entry(123, "NTP"),
            Map.entry(137, "NetBIOS (nomes)"),
            Map.entry(138, "NetBIOS (datagramas)"),
            Map.entry(161, "SNMP"),
            Map.entry(162, "SNMP Trap"),
            Map.entry(443, "QUIC (HTTPS)"),
            Map.entry(514, "Syslog"),
            Map.entry(1900, "SSDP (UPnP)"),
            Map.entry(3702, "WS-Discovery"),
            Map.entry(5353, "mDNS"),
            Map.entry(5355, "LLMNR"));

    /** Serviços TCP comuns no tráfego que não entram na verificação de portas. */
    private static final Map<Integer, String> TCP_EXTRA = Map.of(
            5228, "Google (notificações push)",
            7680, "Otimização de Entrega do Windows",
            8009, "Chromecast",
            5353, "mDNS");

    private TrafficServices() {
    }

    public static String nameOf(TrafficProtocol protocol, Integer port) {
        if (port == null) {
            return null;
        }
        return switch (protocol) {
            case UDP -> UDP.get(port);
            case TCP -> {
                String known = KnownServices.nameOf(port);
                yield known != null ? known : TCP_EXTRA.get(port);
            }
            default -> null;
        };
    }
}
