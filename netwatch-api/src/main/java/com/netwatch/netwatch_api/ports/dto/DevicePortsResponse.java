package com.netwatch.netwatch_api.ports.dto;

import java.time.Instant;
import java.util.List;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.ports.DevicePort;

/**
 * Situação atual das portas de um dispositivo.
 *
 * @param scanning há uma verificação em andamento
 * @param lastScan última verificação (pode estar em andamento), ou nulo
 */
public record DevicePortsResponse(boolean scanning, PortScanResponse lastScan, List<Port> ports) {

    public record Port(int port, PortState state, String service, Instant openSince,
            Instant lastCheckedAt, Instant lastStateChangeAt) {

        public static Port from(DevicePort p) {
            return new Port(p.getPort(), p.getState(), p.getService(), p.getOpenSince(),
                    p.getLastCheckedAt(), p.getLastStateChangeAt());
        }
    }
}
