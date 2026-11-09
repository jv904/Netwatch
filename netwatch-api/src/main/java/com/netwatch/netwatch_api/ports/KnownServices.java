package com.netwatch.netwatch_api.ports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serviços normalmente associados às portas TCP mais comuns. */
public final class KnownServices {

    private static final Map<Integer, String> SERVICES = new LinkedHashMap<>();

    static {
        SERVICES.put(21, "FTP");
        SERVICES.put(22, "SSH");
        SERVICES.put(23, "Telnet");
        SERVICES.put(25, "SMTP");
        SERVICES.put(53, "DNS");
        SERVICES.put(80, "HTTP");
        SERVICES.put(110, "POP3");
        SERVICES.put(135, "Microsoft RPC");
        SERVICES.put(139, "NetBIOS");
        SERVICES.put(143, "IMAP");
        SERVICES.put(443, "HTTPS");
        SERVICES.put(445, "SMB (compartilhamento Windows)");
        SERVICES.put(515, "LPD (impressão)");
        SERVICES.put(554, "RTSP (câmeras/vídeo)");
        SERVICES.put(631, "IPP (impressão)");
        SERVICES.put(993, "IMAPS");
        SERVICES.put(995, "POP3S");
        SERVICES.put(1433, "SQL Server");
        SERVICES.put(1883, "MQTT (IoT)");
        SERVICES.put(3306, "MySQL");
        SERVICES.put(3389, "RDP (Área de Trabalho Remota)");
        SERVICES.put(5000, "UPnP / aplicações web");
        SERVICES.put(5432, "PostgreSQL");
        SERVICES.put(5900, "VNC");
        SERVICES.put(8000, "HTTP alternativo / DVR");
        SERVICES.put(8080, "Aplicações web");
        SERVICES.put(8443, "HTTPS alternativo");
        SERVICES.put(9100, "Impressão direta (JetDirect)");
        SERVICES.put(27017, "MongoDB");
        SERVICES.put(37777, "DVR/NVR (Intelbras/Dahua)");
        SERVICES.put(62078, "Sincronização iPhone/iPad");
    }

    /** Portas usadas na verificação "Portas mais comuns". */
    public static final List<Integer> COMMON_PORTS = List.copyOf(SERVICES.keySet());

    private KnownServices() {
    }

    public static String nameOf(int port) {
        return SERVICES.get(port);
    }
}
