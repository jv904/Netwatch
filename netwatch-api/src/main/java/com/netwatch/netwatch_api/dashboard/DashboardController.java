package com.netwatch.netwatch_api.dashboard;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.device.dto.DeviceSummary;
import com.netwatch.netwatch_api.discovery.DiscoveryRunRepository;
import com.netwatch.netwatch_api.discovery.DiscoveryRunStatus;
import com.netwatch.netwatch_api.discovery.dto.DiscoveryRunResponse;
import com.netwatch.netwatch_api.ports.PortInventoryService;

/** Visão geral da rede em uma única chamada (seção 3 do manual). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int TOP_VENDORS = 8;

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final DiscoveryRunRepository discoveryRuns;
    private final PortInventoryService ports;

    public DashboardController(DeviceService deviceService, DeviceRepository deviceRepository,
            DiscoveryRunRepository discoveryRuns, PortInventoryService ports) {
        this.deviceService = deviceService;
        this.deviceRepository = deviceRepository;
        this.discoveryRuns = discoveryRuns;
        this.ports = ports;
    }

    public record Overview(
            DeviceSummary devices,
            long openPorts,
            DiscoveryRunResponse lastDiscovery,
            List<DiscoveryPoint> discoveryHistory,
            List<VendorShare> topVendors) {
    }

    /** Um ponto do histórico de descobertas concluídas (ordem cronológica). */
    public record DiscoveryPoint(Instant startedAt, int devicesFound, int newDevices) {
    }

    /** {@code vendor} nulo = fabricante não identificado. */
    public record VendorShare(String vendor, long devices) {
    }

    @GetMapping("/overview")
    public Overview overview() {
        List<DiscoveryPoint> history = discoveryRuns.findTop20ByStatusOrderByStartedAtDesc(DiscoveryRunStatus.COMPLETED)
                .stream()
                .map(r -> new DiscoveryPoint(r.getStartedAt(), r.getDevicesFound(), r.getNewDevices()))
                .toList()
                .reversed();
        List<VendorShare> vendors = deviceRepository.countByVendor(Limit.of(TOP_VENDORS)).stream()
                .map(v -> new VendorShare(v.getVendor(), v.getTotal()))
                .toList();
        DiscoveryRunResponse last = discoveryRuns.findFirstByOrderByStartedAtDesc()
                .map(DiscoveryRunResponse::from)
                .orElse(null);
        return new Overview(deviceService.summary(), ports.openPortCount(), last, history, vendors);
    }
}
