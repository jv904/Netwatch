package com.netwatch.netwatch_api.ports;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.netwatch.netwatch_api.activity.ActivityEvent;
import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.common.exception.ConflictException;
import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.common.exception.ResourceNotFoundException;
import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks;

/**
 * Verificação de portas de dispositivos do inventário que estejam em redes autorizadas.
 * Cada verificação roda em segundo plano; um mesmo dispositivo não pode ter duas ao mesmo tempo.
 */
@Service
public class PortScanService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PortScanService.class);

    private final PortScanRepository scans;
    private final PortScanResultRepository results;
    private final DevicePortRepository devicePorts;
    private final DeviceService deviceService;
    private final AuthorizedNetworks authorizedNetworks;
    private final PortProber prober;
    private final PortScanProperties properties;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    private final Set<UUID> scanningDevices = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore connections;

    public PortScanService(PortScanRepository scans, PortScanResultRepository results,
            DevicePortRepository devicePorts, DeviceService deviceService, AuthorizedNetworks authorizedNetworks,
            PortProber prober, PortScanProperties properties, TransactionTemplate transaction,
            ApplicationEventPublisher events, Clock clock) {
        this.scans = scans;
        this.results = results;
        this.devicePorts = devicePorts;
        this.deviceService = deviceService;
        this.authorizedNetworks = authorizedNetworks;
        this.prober = prober;
        this.properties = properties;
        this.transaction = transaction;
        this.events = events;
        this.clock = clock;
        this.connections = new Semaphore(Math.max(1, properties.concurrency()));
    }

    public PortScan start(UUID deviceId, PortScanType type, String portSpec) {
        Device device = deviceService.get(deviceId);
        if (!authorizedNetworks.isAuthorized(device.getIpAddress())) {
            throw new InvalidInputException("O dispositivo " + device.getIpAddress()
                    + " não está em uma rede autorizada para verificação.");
        }
        PortScanType scanType = type != null ? type : PortScanType.COMMON;
        List<Integer> ports = scanType == PortScanType.COMMON
                ? KnownServices.COMMON_PORTS
                : PortSpec.parse(portSpec, properties.maxPorts());
        String requested = scanType == PortScanType.COMMON ? "Portas mais comuns" : portSpec.trim();

        if (!scanningDevices.add(deviceId)) {
            throw new ConflictException("Já existe uma verificação de portas em andamento para este dispositivo.");
        }
        try {
            PortScan scan = scans.save(new PortScan(deviceId, device.getIpAddress(), scanType, requested,
                    ports.size(), clock.instant()));
            UUID scanId = scan.getId();
            executor.execute(() -> execute(scanId, deviceId, device.getIpAddress(), ports));
            return scan;
        } catch (RuntimeException e) {
            scanningDevices.remove(deviceId);
            throw e;
        }
    }

    public boolean isScanning(UUID deviceId) {
        return scanningDevices.contains(deviceId);
    }

    public PortScan get(UUID scanId) {
        return scans.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Verificação de portas não encontrada: " + scanId));
    }

    public List<PortScanResult> resultsOf(UUID scanId) {
        return results.findByScanIdOrderByPort(scanId);
    }

    public Page<PortScan> history(UUID deviceId, Pageable pageable) {
        deviceService.get(deviceId);
        return scans.findByDeviceId(deviceId, pageable);
    }

    public List<DevicePort> currentPorts(UUID deviceId) {
        deviceService.get(deviceId);
        return devicePorts.findByDeviceIdOrderByPort(deviceId);
    }

    public Optional<PortScan> lastScan(UUID deviceId) {
        return scans.findFirstByDeviceIdOrderByStartedAtDesc(deviceId);
    }

    private void execute(UUID scanId, UUID deviceId, String ip, List<Integer> ports) {
        try {
            Map<Integer, PortState> states = probeAll(ip, ports);
            transaction.executeWithoutResult(status -> persist(scanId, deviceId, states));
            log.info("Verificação de portas em {} concluída: {} portas, {} abertas.", ip, ports.size(),
                    states.values().stream().filter(s -> s == PortState.OPEN).count());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(scanId, "Verificação interrompida.");
        } catch (RuntimeException e) {
            log.error("Falha na verificação de portas de {}", ip, e);
            markFailed(scanId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            scanningDevices.remove(deviceId);
        }
    }

    private Map<Integer, PortState> probeAll(String ip, List<Integer> ports) throws InterruptedException {
        Map<Integer, PortState> states = new ConcurrentHashMap<>();
        try (ExecutorService probes = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int port : ports) {
                connections.acquire();
                probes.execute(() -> {
                    try {
                        states.put(port, prober.probe(ip, port));
                    } finally {
                        connections.release();
                    }
                });
            }
        }
        return new TreeMap<>(states);
    }

    private void persist(UUID scanId, UUID deviceId, Map<Integer, PortState> states) {
        Instant now = clock.instant();
        Map<Integer, DevicePort> current = devicePorts
                .findByDeviceIdAndPortIn(deviceId, List.copyOf(states.keySet())).stream()
                .collect(Collectors.toMap(DevicePort::getPort, Function.identity()));

        Map<PortState, Integer> counts = new EnumMap<>(PortState.class);
        Map<Integer, DevicePort> toSave = new HashMap<>();
        List<PortScanResult> rows = new java.util.ArrayList<>(states.size());

        states.forEach((port, state) -> {
            String service = KnownServices.nameOf(port);
            DevicePort devicePort = current.getOrDefault(port, new DevicePort(deviceId, port));
            PortState previous = current.containsKey(port) ? devicePort.getState() : null;
            rows.add(new PortScanResult(scanId, port, state, previous, service));
            devicePort.applyResult(state, service, now);
            toSave.put(port, devicePort);
            counts.merge(state, 1, Integer::sum);
        });

        results.saveAll(rows);
        devicePorts.saveAll(toSave.values());
        PortScan scan = scans.findById(scanId).orElseThrow();
        int open = counts.getOrDefault(PortState.OPEN, 0);
        scan.complete(now, open, counts.getOrDefault(PortState.CLOSED, 0), counts.getOrDefault(PortState.FILTERED, 0));
        publishChanges(scan, rows, open);
    }

    /** Portas que passaram a aceitar ou deixaram de aceitar conexões viram eventos no histórico. */
    private void publishChanges(PortScan scan, List<PortScanResult> rows, int open) {
        String ip = scan.getIpAddress();
        for (PortScanResult r : rows) {
            String port = r.getPort() + (r.getService() != null ? " (" + r.getService() + ")" : "");
            if (r.isNewlyOpened()) {
                publish(ActivityEvent.of(EventType.PORT_OPENED, scan.getDeviceId(), ip,
                        "Porta " + port + " passou a aceitar conexões em " + ip)
                        .withDetails("Antes: " + describe(r.getPreviousState())).withPort(r.getPort()), scan);
            } else if (r.getPreviousState() == PortState.OPEN && r.getState() != PortState.OPEN) {
                publish(ActivityEvent.of(EventType.PORT_CLOSED, scan.getDeviceId(), ip,
                        "Porta " + port + " deixou de aceitar conexões em " + ip)
                        .withDetails("Agora: " + describe(r.getState())).withPort(r.getPort()), scan);
            }
        }
        publish(ActivityEvent.of(EventType.PORT_SCAN_COMPLETED, scan.getDeviceId(), ip,
                "Verificação de portas em " + ip + " concluída: " + open + (open == 1 ? " aberta" : " abertas"))
                .withDetails(scan.getRequestedPorts()), scan);
    }

    private static String describe(PortState state) {
        return switch (state) {
            case OPEN -> "aceitava conexões";
            case CLOSED -> "fechada";
            case FILTERED -> "sem resposta";
        };
    }

    private void publish(ActivityEvent event, PortScan scan) {
        events.publishEvent(event.withReference(scan.getId()));
    }

    private void markFailed(UUID scanId, String message) {
        scans.findById(scanId).ifPresent(scan -> {
            scan.fail(clock.instant(), message);
            scans.save(scan);
            publish(ActivityEvent.of(EventType.PORT_SCAN_FAILED, scan.getDeviceId(), scan.getIpAddress(),
                    "Verificação de portas em " + scan.getIpAddress() + " falhou").withDetails(message), scan);
        });
    }

    /** Verificações em andamento quando a aplicação parou nunca serão concluídas. */
    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedScans() {
        List<PortScan> stale = scans.findByStatus(PortScanStatus.RUNNING);
        stale.removeIf(s -> scanningDevices.contains(s.getDeviceId()));
        if (!stale.isEmpty()) {
            stale.forEach(s -> s.fail(clock.instant(), "Interrompida: a aplicação foi reiniciada."));
            scans.saveAll(stale);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
