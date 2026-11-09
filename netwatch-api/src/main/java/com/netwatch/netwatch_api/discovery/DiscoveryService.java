package com.netwatch.netwatch_api.discovery;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.netwatch.netwatch_api.common.exception.ConflictException;
import com.netwatch.netwatch_api.common.exception.ResourceNotFoundException;
import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks.AuthorizedNetwork;
import com.netwatch.netwatch_api.discovery.NetworkScanner.ScanResult;

/**
 * Coordena as descobertas: valida o escopo, executa em segundo plano (uma por vez),
 * envia os equipamentos encontrados ao inventário e registra o resultado.
 */
@Service
public class DiscoveryService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final DiscoveryRunRepository runs;
    private final NetworkScanner scanner;
    private final AuthorizedNetworks authorizedNetworks;
    private final DeviceService deviceService;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("netwatch-discovery").daemon(true).factory());

    public DiscoveryService(DiscoveryRunRepository runs, NetworkScanner scanner,
            AuthorizedNetworks authorizedNetworks, DeviceService deviceService, Clock clock) {
        this.runs = runs;
        this.scanner = scanner;
        this.authorizedNetworks = authorizedNetworks;
        this.deviceService = deviceService;
        this.clock = clock;
    }

    public List<AuthorizedNetwork> authorizedNetworks() {
        return authorizedNetworks.list();
    }

    /**
     * Inicia uma descoberta em segundo plano.
     *
     * @param cidr faixa a verificar; nula = primeira rede autorizada
     * @return o registro da execução, com status RUNNING
     */
    public DiscoveryRun start(String cidr, DiscoveryTrigger trigger) {
        Ipv4Network network = authorizedNetworks.resolve(cidr);
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("Já existe uma descoberta em andamento. Aguarde a conclusão.");
        }
        DiscoveryRun run;
        try {
            run = runs.save(new DiscoveryRun(network.toString(), trigger, clock.instant()));
            UUID id = run.getId();
            executor.execute(() -> execute(id, network));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return run;
    }

    public Page<DiscoveryRun> list(Pageable pageable) {
        return runs.findAll(pageable);
    }

    public DiscoveryRun get(UUID id) {
        return runs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Descoberta não encontrada: " + id));
    }

    public boolean isRunning() {
        return running.get();
    }

    public Optional<DiscoveryRun> current() {
        return runs.findFirstByStatusOrderByStartedAtDesc(DiscoveryRunStatus.RUNNING);
    }

    private void execute(UUID runId, Ipv4Network network) {
        DiscoveryRun run = runs.findById(runId).orElseThrow();
        try {
            ScanResult result = scanner.scan(network);

            int created = 0;
            int registered = 0;
            for (DeviceObservation observation : result.observations()) {
                try {
                    if (deviceService.registerObservation(observation).created()) {
                        created++;
                    }
                    registered++;
                } catch (RuntimeException e) {
                    log.warn("Não foi possível registrar {}: {}", observation.ipAddress(), e.getMessage());
                }
            }
            int inactivated = deviceService.markInactiveNotSeenSince(run.getStartedAt(), network::contains);

            run.complete(clock.instant(), result.hostsScanned(), registered, created, inactivated);
            log.info("Descoberta {} concluída: {} dispositivos ({} novos, {} inativados).",
                    network, registered, created, inactivated);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.fail(clock.instant(), "Descoberta interrompida.");
        } catch (RuntimeException e) {
            log.error("Falha na descoberta de {}", network, e);
            run.fail(clock.instant(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            try {
                runs.save(run);
            } finally {
                running.set(false);
            }
        }
    }

    /** Execuções que estavam em andamento quando a aplicação parou nunca serão concluídas. */
    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedRuns() {
        List<DiscoveryRun> stale = runs.findByStatus(DiscoveryRunStatus.RUNNING);
        if (!running.get() && !stale.isEmpty()) {
            stale.forEach(r -> r.fail(clock.instant(), "Interrompida: a aplicação foi reiniciada."));
            runs.saveAll(stale);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
