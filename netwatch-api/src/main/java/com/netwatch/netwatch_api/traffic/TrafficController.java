package com.netwatch.netwatch_api.traffic;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.traffic.TrafficQueryService.FlowFilter;
import com.netwatch.netwatch_api.traffic.dto.DeviceTrafficResponse;
import com.netwatch.netwatch_api.traffic.dto.FlowResponse;
import com.netwatch.netwatch_api.traffic.dto.TrafficSummaryResponse;

@RestController
@RequestMapping("/api")
public class TrafficController {

    private final TrafficQueryService traffic;

    public TrafficController(TrafficQueryService traffic) {
        this.traffic = traffic;
    }

    /** Fluxos observados. Ex.: {@code /api/traffic/flows?window=24h&ip=192.168.100.10&protocol=TCP}. */
    @GetMapping("/traffic/flows")
    public PageResponse<FlowResponse> flows(
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) TrafficProtocol protocol,
            @RequestParam(required = false) Integer port,
            @RequestParam(required = false) String window,
            @PageableDefault(size = 50, sort = "lastSeen", direction = Sort.Direction.DESC) Pageable pageable) {
        FlowFilter filter = new FlowFilter(ip, deviceId, protocol, port,
                window != null ? TrafficWindow.parse(window) : null);
        return PageResponse.from(traffic.flows(filter, pageable), f -> f);
    }

    /** Volume, gráfico, protocolos, hosts e serviços no período (1h, 24h ou 7d). */
    @GetMapping("/traffic/summary")
    public TrafficSummaryResponse summary(@RequestParam(required = false) String window) {
        return traffic.summary(TrafficWindow.parse(window));
    }

    @GetMapping("/devices/{deviceId}/traffic")
    public DeviceTrafficResponse deviceTraffic(@PathVariable UUID deviceId,
            @RequestParam(required = false, defaultValue = "24h") String window) {
        return traffic.deviceTraffic(deviceId, TrafficWindow.parse(window));
    }
}
