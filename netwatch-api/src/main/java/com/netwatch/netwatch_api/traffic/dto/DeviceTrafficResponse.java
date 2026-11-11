package com.netwatch.netwatch_api.traffic.dto;

import java.util.List;
import java.util.UUID;

/**
 * Tráfego de um dispositivo no período.
 *
 * @param peers       com quem o dispositivo mais trocou dados
 * @param recentFlows fluxos mais recentes envolvendo o dispositivo
 */
public record DeviceTrafficResponse(
        String window,
        long bytesSent,
        long bytesReceived,
        long packetsSent,
        long packetsReceived,
        long flows,
        List<Peer> peers,
        List<FlowResponse> recentFlows) {

    public record Peer(String ip, UUID deviceId, String name, long bytes, long packets, long flows) {
    }
}
