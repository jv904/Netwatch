package com.netwatch.netwatch_api.traffic.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.netwatch.netwatch_api.traffic.TrafficProtocol;

/**
 * Visão geral do tráfego no período.
 *
 * @param bucketSeconds intervalo entre os pontos de {@code series}
 * @param series        pacotes e bytes por intervalo, em ordem cronológica e sem lacunas
 */
public record TrafficSummaryResponse(
        String window,
        Instant since,
        Instant until,
        long bucketSeconds,
        Totals totals,
        List<Point> series,
        List<ProtocolShare> protocols,
        List<HostShare> topHosts,
        List<ServiceShare> topServices) {

    /**
     * @param hosts       endereços distintos das redes autorizadas que aparecem no tráfego
     * @param connections pico de conexões simultâneas no período (modo tabela de conexões)
     */
    public record Totals(long packets, long bytes, long flows, long hosts, long connections) {
    }

    /**
     * @param connections pico de conexões simultâneas no intervalo (modo tabela de conexões)
     */
    public record Point(Instant at, long packets, long bytes, long connections) {
    }

    public record ProtocolShare(TrafficProtocol protocol, long packets, long bytes) {
    }

    /**
     * @param local o IP pertence a uma rede autorizada
     */
    public record HostShare(String ip, UUID deviceId, String name, boolean local, long bytesSent,
            long bytesReceived, long packets, long flows) {
    }

    public record ServiceShare(TrafficProtocol protocol, int port, String service, long bytes, long packets,
            long flows) {
    }
}
