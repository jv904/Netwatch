package com.netwatch.netwatch_api.traffic.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netwatch.netwatch_api.traffic.CaptureMode;
import com.netwatch.netwatch_api.traffic.CaptureSession;
import com.netwatch.netwatch_api.traffic.CaptureSessionStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaptureSessionResponse(
        UUID id,
        CaptureMode mode,
        String interfaceName,
        String interfaceDescription,
        String localAddress,
        String filter,
        CaptureSessionStatus status,
        Instant startedAt,
        Instant plannedEndAt,
        Instant finishedAt,
        Long durationMs,
        long packets,
        long bytes,
        long droppedPackets,
        int newDevices,
        String errorMessage) {

    public static CaptureSessionResponse from(CaptureSession s) {
        Long duration = s.getFinishedAt() != null
                ? Duration.between(s.getStartedAt(), s.getFinishedAt()).toMillis()
                : null;
        return new CaptureSessionResponse(s.getId(), s.getMode(), s.getInterfaceName(), s.getInterfaceDescription(),
                s.getLocalAddress(), s.getBpfFilter(), s.getStatus(), s.getStartedAt(), s.getPlannedEndAt(),
                s.getFinishedAt(), duration, s.getPackets(), s.getBytes(), s.getDroppedPackets(), s.getNewDevices(),
                s.getErrorMessage());
    }
}
