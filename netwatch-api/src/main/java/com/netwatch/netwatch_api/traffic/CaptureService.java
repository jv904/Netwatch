package com.netwatch.netwatch_api.traffic;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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

import com.netwatch.netwatch_api.activity.ActivityEvent;
import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.common.exception.ConflictException;
import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.common.exception.ResourceNotFoundException;
import com.netwatch.netwatch_api.common.exception.ServiceUnavailableException;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks.AuthorizedNetwork;
import com.netwatch.netwatch_api.discovery.Ipv4Network;
import com.netwatch.netwatch_api.discovery.probe.ArpTable;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces.LocalAddress;
import com.netwatch.netwatch_api.settings.SettingKey;
import com.netwatch.netwatch_api.settings.SettingsService;
import com.netwatch.netwatch_api.traffic.FlowAggregator.FlowKey;
import com.netwatch.netwatch_api.traffic.capture.BpfFilterBuilder;
import com.netwatch.netwatch_api.traffic.capture.PacketMeta;
import com.netwatch.netwatch_api.traffic.capture.PacketSource;
import com.netwatch.netwatch_api.traffic.capture.PacketSource.CaptureInterface;
import com.netwatch.netwatch_api.traffic.capture.PacketSource.CaptureStats;
import com.netwatch.netwatch_api.traffic.capture.PacketSource.InterfaceAddress;
import com.netwatch.netwatch_api.traffic.capture.PacketSource.SourceStatus;
import com.netwatch.netwatch_api.traffic.connections.ConnectionEntry;
import com.netwatch.netwatch_api.traffic.connections.ConnectionTable;

/**
 * Sessões de observação de tráfego. Só uma sessão roda por vez, em uma thread própria; o tráfego é
 * agregado em memória e gravado a cada {@code flush-interval}.
 * <p>
 * Duas fontes possíveis ({@link CaptureMode}): captura de pacotes (Npcap/libpcap) ou, sem driver,
 * a tabela de conexões do sistema. Nas duas, só entra o que tem origem ou destino em rede autorizada.
 */
@Service
public class CaptureService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    /** A tabela ARP é relida no máximo a cada 30 s durante uma sessão por conexões. */
    private static final Duration ARP_REFRESH = Duration.ofSeconds(30);

    private final CaptureSessionRepository sessions;
    private final TrafficRecorder recorder;
    private final PacketSource packetSource;
    private final ConnectionTable connectionTable;
    private final LocalInterfaces localInterfaces;
    private final ArpTable arpTable;
    private final AuthorizedNetworks authorizedNetworks;
    private final CaptureProperties properties;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    private final AtomicReference<Running> running = new AtomicReference<>();
    private final ExecutorService captureThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "netwatch-capture");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "netwatch-capture-flush");
        t.setDaemon(true);
        return t;
    });

    public CaptureService(CaptureSessionRepository sessions, TrafficRecorder recorder, PacketSource packetSource,
            ConnectionTable connectionTable, LocalInterfaces localInterfaces, ArpTable arpTable,
            AuthorizedNetworks authorizedNetworks, CaptureProperties properties, SettingsService settings,
            ApplicationEventPublisher events, Clock clock) {
        this.sessions = sessions;
        this.recorder = recorder;
        this.packetSource = packetSource;
        this.connectionTable = connectionTable;
        this.localInterfaces = localInterfaces;
        this.arpTable = arpTable;
        this.authorizedNetworks = authorizedNetworks;
        this.properties = properties;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    /** Uma interface de rede e o endereço dela que está em rede autorizada (nulo = não pode ser capturada). */
    public record InterfaceOption(CaptureInterface iface, InterfaceAddress authorizedAddress) {

        public boolean eligible() {
            return authorizedAddress != null && !iface.loopback();
        }
    }

    /**
     * O que está disponível para observar o tráfego.
     *
     * @param mode   fonte que será usada na próxima sessão; nulo = nenhuma
     * @param reason por que não há fonte, ou a limitação da fonte escolhida
     */
    public record Availability(boolean enabled, CaptureMode mode, boolean packetCapture, boolean connectionTable,
            String libraryVersion, String reason) {

        public boolean available() {
            return mode != null;
        }
    }

    private static final class Running {
        final CaptureMode mode;
        final String interfaceName;
        final String filter;
        final List<Ipv4Network> networks;
        final Instant deadline;
        final FlowAggregator aggregator = new FlowAggregator();
        final AtomicLong packets = new AtomicLong();
        final AtomicLong bytes = new AtomicLong();
        volatile TrafficRecorder.SessionState state;
        volatile boolean stopRequested;
        Map<String, String> arp = Map.of();
        Instant arpReadAt = Instant.EPOCH;

        Running(CaptureMode mode, String interfaceName, String filter, List<Ipv4Network> networks, Instant deadline) {
            this.mode = mode;
            this.interfaceName = interfaceName;
            this.filter = filter;
            this.networks = networks;
            this.deadline = deadline;
        }
    }

    public boolean isEnabled() {
        return settings.bool(SettingKey.CAPTURE_ENABLED);
    }

    /** Duração padrão e máxima de uma sessão (tela de Configurações). */
    public Duration defaultDuration() {
        return settings.minutes(SettingKey.CAPTURE_DEFAULT_DURATION);
    }

    public Duration maxDuration() {
        return settings.minutes(SettingKey.CAPTURE_MAX_DURATION);
    }

    public int retentionDays() {
        return settings.integer(SettingKey.CAPTURE_RETENTION_DAYS);
    }

    public Availability availability() {
        if (!isEnabled()) {
            return new Availability(false, null, false, false, null,
                    "A observação de tráfego está desativada (NETWATCH_CAPTURE_ENABLED=false).");
        }
        String configured = settings.value(SettingKey.CAPTURE_MODE).toLowerCase(Locale.ROOT);
        boolean tableAvailable = connectionTable.available();
        if (configured.equals("connections")) {
            return new Availability(true, tableAvailable ? CaptureMode.CONNECTIONS : null, false, tableAvailable, null,
                    tableAvailable ? connectionsLimitation("Modo tabela de conexões configurado")
                            : "A tabela de conexões só está disponível no Windows.");
        }
        // Só tenta carregar o driver quando a captura de pacotes pode ser usada.
        SourceStatus packets = packetSource.status();
        if (packets.available()) {
            return new Availability(true, CaptureMode.PACKETS, true, tableAvailable, packets.libraryVersion(), null);
        }
        if (configured.equals("packets") || !tableAvailable) {
            return new Availability(true, null, false, tableAvailable, null, packets.reason());
        }
        return new Availability(true, CaptureMode.CONNECTIONS, false, true, null,
                connectionsLimitation("Npcap não encontrado"));
    }

    private static String connectionsLimitation(String prefix) {
        return prefix + ": mostrando as conexões desta máquina pela tabela do sistema, "
                + "sem volume de dados e sem o tráfego de outros equipamentos.";
    }

    public List<Ipv4Network> authorizedNetworks() {
        return authorizedNetworks.list().stream().map(AuthorizedNetwork::network).distinct().toList();
    }

    /** Interfaces para captura de pacotes; vazio no modo tabela de conexões (não há o que escolher). */
    public List<InterfaceOption> interfaces() {
        if (availability().mode() != CaptureMode.PACKETS) {
            return List.of();
        }
        List<Ipv4Network> networks = authorizedNetworks();
        return packetSource.interfaces().stream()
                .map(i -> new InterfaceOption(i, i.addresses().stream()
                        .filter(a -> networks.stream().anyMatch(n -> n.contains(a.ipAddress())))
                        .findFirst().orElse(null)))
                .toList();
    }

    public CaptureSession start(String interfaceName, Integer durationMinutes) {
        Availability availability = availability();
        if (!availability.available()) {
            throw new ServiceUnavailableException(availability.reason());
        }
        Duration duration = durationMinutes != null ? Duration.ofMinutes(durationMinutes) : defaultDuration();
        if (duration.compareTo(Duration.ofMinutes(1)) < 0 || duration.compareTo(maxDuration()) > 0) {
            throw new InvalidInputException("A duração deve ficar entre 1 minuto e "
                    + maxDuration().toMinutes() + " minutos.");
        }
        List<Ipv4Network> networks = authorizedNetworks();
        if (networks.isEmpty()) {
            throw new InvalidInputException(
                    "Nenhuma rede autorizada foi encontrada. Configure netwatch.discovery.authorized-networks.");
        }
        Instant now = clock.instant();
        Instant deadline = now.plus(duration);

        String name;
        String description;
        String localIp;
        int prefix;
        String filter;
        if (availability.mode() == CaptureMode.PACKETS) {
            InterfaceOption option = chooseInterface(interfaceName);
            name = option.iface().name();
            description = option.iface().description();
            localIp = option.authorizedAddress().ipAddress();
            prefix = option.authorizedAddress().prefixLength();
            filter = BpfFilterBuilder.build(networks);
        } else {
            LocalAddress local = localInterfaces.list().stream()
                    .sorted(Comparator.comparing(LocalAddress::virtual))
                    .filter(a -> networks.stream().anyMatch(n -> n.contains(a.ipAddress())))
                    .findFirst()
                    .orElseThrow(() -> new InvalidInputException(
                            "Esta máquina não tem endereço em uma rede autorizada."));
            name = "connections";
            description = "Tabela de conexões do sistema";
            localIp = local.ipAddress();
            prefix = local.prefixLength();
            filter = networks.stream().map(Object::toString).collect(Collectors.joining(", "));
        }
        Ipv4Network localSubnet = new Ipv4Network(Ipv4Network.toInt(localIp).orElseThrow(), prefix);

        Running run = new Running(availability.mode(), name, filter, networks, deadline);
        if (!running.compareAndSet(null, run)) {
            throw new ConflictException("Já existe uma captura em andamento. Pare-a antes de iniciar outra.");
        }
        try {
            CaptureSession session = sessions.save(new CaptureSession(availability.mode(), name, description, localIp,
                    filter, now, deadline));
            run.state = new TrafficRecorder.SessionState(session.getId(), localSubnet);
            events.publishEvent(ActivityEvent.of(EventType.CAPTURE_STARTED, null, localIp,
                    (run.mode == CaptureMode.CONNECTIONS ? "Observação das conexões iniciada em " : "Captura iniciada em ")
                            + description)
                    .withDetails("Escopo: " + filter).withReference(session.getId()));
            captureThread.execute(() -> execute(run));
            log.info("Captura ({}) iniciada em {} ({}), escopo \"{}\", até {}.", run.mode, description, localIp,
                    filter, deadline);
            return session;
        } catch (RuntimeException e) {
            running.compareAndSet(run, null);
            throw e;
        }
    }

    private InterfaceOption chooseInterface(String requested) {
        List<InterfaceOption> options = interfaces();
        String wanted = requested != null && !requested.isBlank() ? requested.trim() : properties.defaultInterface();
        if (wanted == null || wanted.isBlank()) {
            return options.stream().filter(InterfaceOption::eligible).findFirst()
                    .orElseThrow(() -> new InvalidInputException(
                            "Nenhuma interface de rede tem endereço em uma rede autorizada."));
        }
        String needle = wanted.toLowerCase(Locale.ROOT);
        InterfaceOption option = options.stream()
                .filter(o -> o.iface().name().equals(wanted))
                .findFirst()
                .or(() -> options.stream()
                        .filter(o -> o.iface().description() != null
                                && o.iface().description().toLowerCase(Locale.ROOT).contains(needle))
                        .findFirst())
                .orElseThrow(() -> new InvalidInputException("Interface de rede não encontrada: " + wanted));
        if (!option.eligible()) {
            throw new InvalidInputException("A interface " + option.iface().description()
                    + " não tem endereço em uma rede autorizada e não pode ser capturada.");
        }
        return option;
    }

    private boolean shouldStop(Running run) {
        return run.stopRequested || !clock.instant().isBefore(run.deadline);
    }

    private void execute(Running run) {
        long interval = Math.max(1000, properties.flushInterval().toMillis());
        ScheduledFuture<?> periodic = flusher.scheduleWithFixedDelay(() -> flush(run), interval, interval,
                TimeUnit.MILLISECONDS);
        CaptureStats stats = CaptureStats.NONE;
        String error = null;
        try {
            if (run.mode == CaptureMode.PACKETS) {
                stats = packetSource.capture(run.interfaceName, run.filter, properties.snaplen(),
                        properties.promiscuous(), packet -> accept(run, packet), () -> shouldStop(run));
            } else {
                pollConnections(run);
            }
        } catch (RuntimeException e) {
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Captura em {} falhou: {}", run.interfaceName, error);
        } finally {
            periodic.cancel(false);
            flush(run);
            finish(run, stats, error);
            running.compareAndSet(run, null);
        }
    }

    private void accept(Running run, PacketMeta packet) {
        if (!inScope(run.networks, packet.srcIp()) && !inScope(run.networks, packet.dstIp())) {
            return;
        }
        run.aggregator.add(packet);
        run.packets.incrementAndGet();
        run.bytes.addAndGet(packet.length());
    }

    /** Lê a tabela de conexões a cada {@code connection-poll-interval} até a sessão terminar. */
    private void pollConnections(Running run) {
        long pollMs = Math.max(1000, settings.integer(SettingKey.CAPTURE_POLL_SECONDS) * 1000L);
        while (!shouldStop(run)) {
            sampleConnections(run);
            long next = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(pollMs);
            while (!shouldStop(run) && System.nanoTime() < next) {
                try {
                    Thread.sleep(Math.min(200, pollMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void sampleConnections(Running run) {
        List<ConnectionEntry> entries = connectionTable.snapshot();
        Instant now = clock.instant();
        Set<Integer> listening = new HashSet<>();
        for (ConnectionEntry e : entries) {
            if (e.protocol() == TrafficProtocol.TCP && e.isListening()) {
                listening.add(e.localPort());
            }
        }
        Map<TrafficProtocol, Long> concurrent = new EnumMap<>(TrafficProtocol.class);
        for (ConnectionEntry e : entries) {
            if (e.remoteIp() == null || e.isListening()) {
                continue;
            }
            if (!inScope(run.networks, e.localIp()) && !inScope(run.networks, e.remoteIp())) {
                continue;
            }
            boolean inbound = e.protocol() == TrafficProtocol.TCP && listening.contains(e.localPort());
            String remoteMac = macOf(run, e.remoteIp());
            FlowKey key = inbound
                    ? new FlowKey(e.protocol(), e.remoteIp(), e.remotePort(), e.localIp(), e.localPort())
                    : new FlowKey(e.protocol(), e.localIp(), e.localPort(), e.remoteIp(), e.remotePort());
            run.aggregator.addConnection(key, now, inbound ? remoteMac : null, inbound ? null : remoteMac,
                    e.processName(), e.state());
            concurrent.merge(e.protocol(), 1L, Long::sum);
        }
        concurrent.forEach((protocol, count) -> run.aggregator.observeConcurrent(now, protocol, count));
    }

    /** MAC de um vizinho da sub-rede local, pela tabela ARP (fora dela não há MAC útil). */
    private String macOf(Running run, String ip) {
        if (run.state == null || run.state.localSubnet == null || !run.state.localSubnet.contains(ip)) {
            return null;
        }
        Instant now = clock.instant();
        if (Duration.between(run.arpReadAt, now).compareTo(ARP_REFRESH) > 0) {
            run.arp = arpTable.read();
            run.arpReadAt = now;
        }
        return run.arp.get(ip);
    }

    private static boolean inScope(List<Ipv4Network> networks, String ip) {
        for (Ipv4Network network : networks) {
            if (network.contains(ip)) {
                return true;
            }
        }
        return false;
    }

    private void flush(Running run) {
        synchronized (run) {
            try {
                FlowAggregator.Drain drain = run.aggregator.drain();
                recorder.observeDevices(run.state, drain);
                recorder.persist(run.state, drain, run.packets.get(), run.bytes.get());
            } catch (RuntimeException e) {
                log.error("Falha ao gravar o tráfego capturado", e);
            }
        }
    }

    private void finish(Running run, CaptureStats stats, String error) {
        CaptureSessionStatus status = error != null ? CaptureSessionStatus.FAILED
                : run.stopRequested ? CaptureSessionStatus.STOPPED
                : CaptureSessionStatus.COMPLETED;
        try {
            sessions.findById(run.state.sessionId).ifPresent(session -> {
                session.updateCounters(run.packets.get(), run.bytes.get(), run.state.newDevices);
                session.finish(status, clock.instant(), stats.dropped(), error);
                sessions.save(session);
                events.publishEvent(finishedEvent(run, session).withReference(session.getId()));
            });
            log.info("Captura encerrada ({}): {} pacotes, {} bytes.", status, run.packets.get(), run.bytes.get());
        } catch (RuntimeException e) {
            log.error("Falha ao encerrar a sessão de captura", e);
        }
    }

    private static ActivityEvent finishedEvent(Running run, CaptureSession session) {
        String what = run.mode == CaptureMode.CONNECTIONS ? "Observação das conexões" : "Captura";
        if (session.getStatus() == CaptureSessionStatus.FAILED) {
            return ActivityEvent.of(EventType.CAPTURE_FAILED, null, session.getLocalAddress(), what + " falhou")
                    .withDetails(session.getErrorMessage());
        }
        String how = session.getStatus() == CaptureSessionStatus.STOPPED ? "parada" : "concluída";
        String details = run.mode == CaptureMode.CONNECTIONS
                ? session.getNewDevices() + " novos dispositivos"
                : session.getPackets() + " pacotes, " + session.getBytes() + " bytes, " + session.getNewDevices()
                        + " novos dispositivos";
        return ActivityEvent.of(EventType.CAPTURE_FINISHED, null, session.getLocalAddress(), what + " " + how)
                .withDetails(details);
    }

    /** Pede a parada; a sessão termina em até meio segundo e a última gravação acontece em seguida. */
    public CaptureSession stop(UUID sessionId) {
        CaptureSession session = get(sessionId);
        Running run = running.get();
        if (run == null || run.state == null || !run.state.sessionId.equals(sessionId)) {
            throw new ConflictException("Esta sessão de captura não está em andamento.");
        }
        run.stopRequested = true;
        return session;
    }

    public CaptureSession get(UUID sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão de captura não encontrada: " + sessionId));
    }

    /** Sessão em andamento, se houver. */
    public Optional<CaptureSession> active() {
        Running run = running.get();
        if (run == null || run.state == null) {
            return Optional.empty();
        }
        return sessions.findById(run.state.sessionId);
    }

    public Optional<CaptureSession> last() {
        return sessions.findFirstByOrderByStartedAtDesc();
    }

    public Page<CaptureSession> history(Pageable pageable) {
        return sessions.findAll(pageable);
    }

    /** Sessões que estavam rodando quando a aplicação parou nunca terminarão. */
    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedSessions() {
        Running run = running.get();
        List<CaptureSession> stale = sessions.findByStatus(CaptureSessionStatus.RUNNING);
        stale.removeIf(s -> run != null && run.state != null && run.state.sessionId.equals(s.getId()));
        if (!stale.isEmpty()) {
            stale.forEach(s -> s.finish(CaptureSessionStatus.FAILED, clock.instant(), 0,
                    "Interrompida: a aplicação foi reiniciada."));
            sessions.saveAll(stale);
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        Running run = running.get();
        if (run != null) {
            run.stopRequested = true;
        }
        captureThread.shutdown();
        captureThread.awaitTermination(5, TimeUnit.SECONDS);
        flusher.shutdownNow();
    }
}
