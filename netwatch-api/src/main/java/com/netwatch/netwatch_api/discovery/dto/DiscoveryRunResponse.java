package com.netwatch.netwatch_api.discovery.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.netwatch.netwatch_api.discovery.DiscoveryRun;
import com.netwatch.netwatch_api.discovery.DiscoveryRunStatus;
import com.netwatch.netwatch_api.discovery.DiscoveryTrigger;

public record DiscoveryRunResponse(
        UUID id,
        String cidr,
        DiscoveryRunStatus status,
        DiscoveryTrigger trigger,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        int hostsScanned,
        int devicesFound,
        int newDevices,
        int inactivated,
        String errorMessage) {

    public static DiscoveryRunResponse from(DiscoveryRun run) {
        Long duration = run.getFinishedAt() != null
                ? Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis()
                : null;
        return new DiscoveryRunResponse(
                run.getId(),
                run.getCidr(),
                run.getStatus(),
                run.getTrigger(),
                run.getStartedAt(),
                run.getFinishedAt(),
                duration,
                run.getHostsScanned(),
                run.getDevicesFound(),
                run.getNewDevices(),
                run.getInactivated(),
                run.getErrorMessage());
    }
}
