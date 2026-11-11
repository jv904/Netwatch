package com.netwatch.netwatch_api.activity.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.netwatch.netwatch_api.activity.EventCategory;
import com.netwatch.netwatch_api.activity.EventSeverity;
import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.activity.NetworkEvent;

/**
 * Um evento do histórico. {@code deviceName} vem do inventário (nulo se o dispositivo foi removido).
 */
public record EventResponse(
        UUID id,
        Instant occurredAt,
        EventType type,
        EventCategory category,
        EventSeverity severity,
        String title,
        String details,
        UUID deviceId,
        String deviceName,
        String ipAddress,
        UUID referenceId) {

    public static EventResponse from(NetworkEvent e, Map<UUID, String> deviceNames) {
        return new EventResponse(e.getId(), e.getOccurredAt(), e.getType(), e.getCategory(), e.getSeverity(),
                e.getTitle(), e.getDetails(), e.getDeviceId(),
                e.getDeviceId() != null ? deviceNames.get(e.getDeviceId()) : null,
                e.getIpAddress(), e.getReferenceId());
    }
}
