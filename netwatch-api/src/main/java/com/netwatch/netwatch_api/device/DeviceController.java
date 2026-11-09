package com.netwatch.netwatch_api.device;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.device.dto.CreateDeviceRequest;
import com.netwatch.netwatch_api.device.dto.DeviceResponse;
import com.netwatch.netwatch_api.device.dto.DeviceSummary;
import com.netwatch.netwatch_api.device.dto.RecognitionRequest;
import com.netwatch.netwatch_api.device.dto.UpdateDeviceRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    /**
     * Lista dispositivos com filtros opcionais.
     * Ex.: {@code GET /api/devices?recognized=false&status=ACTIVE&q=192.168&sort=lastSeenAt,desc}
     */
    @GetMapping
    public PageResponse<DeviceResponse> list(
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(required = false) DeviceType type,
            @RequestParam(required = false) Boolean recognized,
            @RequestParam(name = "q", required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        DeviceFilter filter = new DeviceFilter(status, type, recognized, search);
        return PageResponse.from(service.search(filter, pageable), DeviceResponse::from);
    }

    @GetMapping("/summary")
    public DeviceSummary summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable UUID id) {
        return DeviceResponse.from(service.get(id));
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(@Valid @RequestBody CreateDeviceRequest request) {
        Device device = service.create(request);
        return ResponseEntity.created(URI.create("/api/devices/" + device.getId()))
                .body(DeviceResponse.from(device));
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest request) {
        return DeviceResponse.from(service.update(id, request));
    }

    /** Marca como conhecido / desconhecido (fluxo da seção 4.3). */
    @PatchMapping("/{id}/recognition")
    public DeviceResponse setRecognition(@PathVariable UUID id, @Valid @RequestBody RecognitionRequest request) {
        return DeviceResponse.from(service.setRecognized(id, request.recognized()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
