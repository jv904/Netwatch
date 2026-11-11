package com.netwatch.netwatch_api.traffic;

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
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.traffic.dto.CaptureInterfaceResponse;
import com.netwatch.netwatch_api.traffic.dto.CaptureSessionResponse;
import com.netwatch.netwatch_api.traffic.dto.CaptureStatusResponse;
import com.netwatch.netwatch_api.traffic.dto.StartCaptureRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/capture")
public class CaptureController {

    private final CaptureService capture;
    private final CaptureProperties properties;

    public CaptureController(CaptureService capture, CaptureProperties properties) {
        this.capture = capture;
        this.properties = properties;
    }

    /** Situação da captura: driver, redes autorizadas e sessão em andamento. */
    @GetMapping("/status")
    public CaptureStatusResponse status() {
        CaptureService.Availability source = capture.availability();
        return new CaptureStatusResponse(
                source.enabled(),
                source.available(),
                source.mode(),
                source.packetCapture(),
                source.connectionTable(),
                source.reason(),
                source.libraryVersion(),
                capture.authorizedNetworks().stream().map(Object::toString).toList(),
                (int) properties.maxDuration().toMinutes(),
                (int) properties.defaultDuration().toMinutes(),
                properties.retentionDays(),
                capture.active().map(CaptureSessionResponse::from).orElse(null),
                capture.last().map(CaptureSessionResponse::from).orElse(null));
    }

    @GetMapping("/interfaces")
    public List<CaptureInterfaceResponse> interfaces() {
        return capture.interfaces().stream().map(CaptureInterfaceResponse::from).toList();
    }

    /** Inicia uma sessão de captura (responde 202 imediatamente). */
    @PostMapping("/sessions")
    public ResponseEntity<CaptureSessionResponse> start(@Valid @RequestBody(required = false) StartCaptureRequest request) {
        CaptureSession session = capture.start(
                request != null ? request.interfaceName() : null,
                request != null ? request.durationMinutes() : null);
        return ResponseEntity.accepted()
                .location(URI.create("/api/capture/sessions/" + session.getId()))
                .body(CaptureSessionResponse.from(session));
    }

    @PostMapping("/sessions/{id}/stop")
    public ResponseEntity<CaptureSessionResponse> stop(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(CaptureSessionResponse.from(capture.stop(id)));
    }

    @GetMapping("/sessions/{id}")
    public CaptureSessionResponse get(@PathVariable UUID id) {
        return CaptureSessionResponse.from(capture.get(id));
    }

    @GetMapping("/sessions")
    public PageResponse<CaptureSessionResponse> history(
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(capture.history(pageable), CaptureSessionResponse::from);
    }
}
