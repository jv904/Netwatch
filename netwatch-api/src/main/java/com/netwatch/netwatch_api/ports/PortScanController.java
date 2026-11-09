package com.netwatch.netwatch_api.ports;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.ports.dto.DevicePortsResponse;
import com.netwatch.netwatch_api.ports.dto.NetworkPortResponse;
import com.netwatch.netwatch_api.ports.dto.PortScanResponse;
import com.netwatch.netwatch_api.ports.dto.StartPortScanRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PortScanController {

    private final PortScanService scans;
    private final PortInventoryService inventory;

    public PortScanController(PortScanService scans, PortInventoryService inventory) {
        this.scans = scans;
        this.inventory = inventory;
    }

    /** Inicia uma verificação de portas no dispositivo (responde 202 imediatamente). */
    @PostMapping("/devices/{deviceId}/port-scans")
    public ResponseEntity<PortScanResponse> start(@PathVariable UUID deviceId,
            @Valid @RequestBody(required = false) StartPortScanRequest request) {
        PortScanType type = request != null ? request.type() : null;
        String ports = request != null ? request.ports() : null;
        PortScan scan = scans.start(deviceId, type, ports);
        return ResponseEntity.accepted()
                .location(URI.create("/api/port-scans/" + scan.getId()))
                .body(PortScanResponse.from(scan));
    }

    @GetMapping("/devices/{deviceId}/port-scans")
    public PageResponse<PortScanResponse> history(@PathVariable UUID deviceId,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(scans.history(deviceId, pageable), PortScanResponse::from);
    }

    @GetMapping("/devices/{deviceId}/ports")
    public DevicePortsResponse devicePorts(@PathVariable UUID deviceId) {
        List<DevicePortsResponse.Port> ports = scans.currentPorts(deviceId).stream()
                .map(DevicePortsResponse.Port::from).toList();
        PortScanResponse last = scans.lastScan(deviceId).map(PortScanResponse::from).orElse(null);
        return new DevicePortsResponse(scans.isScanning(deviceId), last, ports);
    }

    @GetMapping("/port-scans/{scanId}")
    public PortScanResponse get(@PathVariable UUID scanId) {
        return PortScanResponse.from(scans.get(scanId), scans.resultsOf(scanId));
    }

    /** Portas de todos os dispositivos. Ex.: {@code /api/ports?state=OPEN&q=hikvision}. */
    @GetMapping("/ports")
    public PageResponse<NetworkPortResponse> ports(
            @RequestParam(required = false) PortState state,
            @RequestParam(required = false) Integer port,
            @RequestParam(name = "q", required = false) String search,
            @PageableDefault(size = 50, sort = "port") Pageable pageable) {
        return PageResponse.from(inventory.search(state, port, search, pageable), r -> r);
    }

    /** Serviços mais expostos na rede (portas abertas agrupadas). */
    @GetMapping("/ports/services")
    public List<ExposedServiceResponse> exposedServices() {
        return inventory.exposedServices().stream()
                .map(s -> new ExposedServiceResponse(s.getPort(), s.getService(), s.getDevices()))
                .toList();
    }

    public record ExposedServiceResponse(int port, String service, long devices) {
    }
}
