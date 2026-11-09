package com.netwatch.netwatch_api.device.dto;

import java.time.Instant;
import java.util.UUID;

import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceStatus;
import com.netwatch.netwatch_api.device.DeviceType;

public record DeviceResponse(
        UUID id,
        String displayName,
        String ipAddress,
        String macAddress,
        String hostname,
        String vendor,
        String customName,
        DeviceType type,
        DeviceStatus status,
        boolean recognized,
        String notes,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceResponse from(Device d) {
        return new DeviceResponse(
                d.getId(),
                displayName(d),
                d.getIpAddress(),
                d.getMacAddress(),
                d.getHostname(),
                d.getVendor(),
                d.getCustomName(),
                d.getType(),
                d.getStatus(),
                d.isRecognized(),
                d.getNotes(),
                d.getFirstSeenAt(),
                d.getLastSeenAt(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    /** Nome amigável: nome do usuário > hostname > "Dispositivo desconhecido". */
    private static String displayName(Device d) {
        if (d.getCustomName() != null && !d.getCustomName().isBlank()) {
            return d.getCustomName();
        }
        if (d.getHostname() != null && !d.getHostname().isBlank()) {
            return d.getHostname();
        }
        return "Dispositivo desconhecido";
    }
}
