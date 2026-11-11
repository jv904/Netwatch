package com.netwatch.netwatch_api.traffic.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.netwatch.netwatch_api.traffic.TrafficFlow;
import com.netwatch.netwatch_api.traffic.TrafficProtocol;
import com.netwatch.netwatch_api.traffic.TrafficServices;

/**
 * Um fluxo de tráfego. {@code srcName}/{@code dstName} vêm do inventário quando o IP é de um dispositivo conhecido.
 */
public record FlowResponse(
        UUID id,
        UUID sessionId,
        TrafficProtocol protocol,
        String srcIp,
        Integer srcPort,
        UUID srcDeviceId,
        String srcName,
        String dstIp,
        Integer dstPort,
        UUID dstDeviceId,
        String dstName,
        Integer servicePort,
        String service,
        String processName,
        String connectionState,
        long packets,
        long bytes,
        int tcpFlags,
        Instant firstSeen,
        Instant lastSeen) {

    public static FlowResponse from(TrafficFlow f, Map<UUID, String> deviceNames) {
        return new FlowResponse(f.getId(), f.getSessionId(), f.getProtocol(),
                f.getSrcIp(), f.getSrcPort(), f.getSrcDeviceId(), name(f.getSrcDeviceId(), deviceNames),
                f.getDstIp(), f.getDstPort(), f.getDstDeviceId(), name(f.getDstDeviceId(), deviceNames),
                f.getServicePort(), TrafficServices.nameOf(f.getProtocol(), f.getServicePort()),
                f.getProcessName(), f.getConnectionState(), f.getPackets(), f.getBytes(), f.getTcpFlags(), f.getFirstSeen(), f.getLastSeen());
    }

    private static String name(UUID id, Map<UUID, String> names) {
        return id != null ? names.get(id) : null;
    }
}
