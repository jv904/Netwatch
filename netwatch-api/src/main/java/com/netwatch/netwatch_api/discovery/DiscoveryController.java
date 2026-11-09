package com.netwatch.netwatch_api.discovery;

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
import com.netwatch.netwatch_api.discovery.dto.AuthorizedNetworkResponse;
import com.netwatch.netwatch_api.discovery.dto.DiscoveryRunResponse;
import com.netwatch.netwatch_api.discovery.dto.StartDiscoveryRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryService service;

    public DiscoveryController(DiscoveryService service) {
        this.service = service;
    }

    /** Redes que o NetWatch tem permissão para verificar. */
    @GetMapping("/networks")
    public List<AuthorizedNetworkResponse> networks() {
        return service.authorizedNetworks().stream().map(AuthorizedNetworkResponse::from).toList();
    }

    /** "Nova descoberta": inicia em segundo plano e responde 202 imediatamente. */
    @PostMapping("/runs")
    public ResponseEntity<DiscoveryRunResponse> start(@Valid @RequestBody(required = false) StartDiscoveryRequest request) {
        String cidr = request != null ? request.cidr() : null;
        DiscoveryRun run = service.start(cidr, DiscoveryTrigger.MANUAL);
        return ResponseEntity.accepted()
                .location(URI.create("/api/discovery/runs/" + run.getId()))
                .body(DiscoveryRunResponse.from(run));
    }

    @GetMapping("/runs")
    public PageResponse<DiscoveryRunResponse> list(
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.list(pageable), DiscoveryRunResponse::from);
    }

    /** Execução em andamento, ou 204 se não houver. */
    @GetMapping("/runs/current")
    public ResponseEntity<DiscoveryRunResponse> current() {
        return service.current()
                .map(run -> ResponseEntity.ok(DiscoveryRunResponse.from(run)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/runs/{id}")
    public DiscoveryRunResponse get(@PathVariable UUID id) {
        return DiscoveryRunResponse.from(service.get(id));
    }
}
