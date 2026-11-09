package com.netwatch.netwatch_api.ports.dto;

import java.time.Instant;
import java.util.UUID;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.dto.DeviceResponse;
import com.netwatch.netwatch_api.ports.DevicePort;

/** Porta de um dispositivo, com os dados do dispositivo para exibição em listas gerais. */
public record NetworkPortResponse(
        UUID deviceId,
        String deviceName,
        String ipAddress,
        String vendor,
        int port,
        PortState state,
        String service,
        Instant openSince,
        Instant lastCheckedAt) {

    public static NetworkPortResponse from(DevicePort port, Device device) {
        DeviceResponse d = device != null ? DeviceResponse.from(device) : null;
        return new NetworkPortResponse(
                port.getDeviceId(),
                d != null ? d.displayName() : null,
                d != null ? d.ipAddress() : null,
                d != null ? d.vendor() : null,
                port.getPort(),
                port.getState(),
                port.getService(),
                port.getOpenSince(),
                port.getLastCheckedAt());
    }
}
