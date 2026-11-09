package com.netwatch.netwatch_api.ports.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.ports.PortScan;
import com.netwatch.netwatch_api.ports.PortScanResult;
import com.netwatch.netwatch_api.ports.PortScanStatus;
import com.netwatch.netwatch_api.ports.PortScanType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortScanResponse(
        UUID id,
        UUID deviceId,
        String ipAddress,
        PortScanType type,
        String requestedPorts,
        int portCount,
        PortScanStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        int openCount,
        int closedCount,
        int filteredCount,
        String errorMessage,
        List<Result> results) {

    public record Result(int port, PortState state, PortState previousState, boolean newlyOpened, String service) {

        static Result from(PortScanResult r) {
            return new Result(r.getPort(), r.getState(), r.getPreviousState(), r.isNewlyOpened(), r.getService());
        }
    }

    public static PortScanResponse from(PortScan scan) {
        return from(scan, null);
    }

    public static PortScanResponse from(PortScan scan, List<PortScanResult> results) {
        Long duration = scan.getFinishedAt() != null
                ? Duration.between(scan.getStartedAt(), scan.getFinishedAt()).toMillis()
                : null;
        return new PortScanResponse(
                scan.getId(),
                scan.getDeviceId(),
                scan.getIpAddress(),
                scan.getType(),
                scan.getRequestedPorts(),
                scan.getPortCount(),
                scan.getStatus(),
                scan.getStartedAt(),
                scan.getFinishedAt(),
                duration,
                scan.getOpenCount(),
                scan.getClosedCount(),
                scan.getFilteredCount(),
                scan.getErrorMessage(),
                results != null ? results.stream().map(Result::from).toList() : null);
    }
}
