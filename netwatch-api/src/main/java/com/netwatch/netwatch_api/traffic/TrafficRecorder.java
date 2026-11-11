package com.netwatch.netwatch_api.traffic;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.device.ObservationResult;
import com.netwatch.netwatch_api.discovery.Ipv4Network;
import com.netwatch.netwatch_api.discovery.vendor.MacVendorLookup;
import com.netwatch.netwatch_api.traffic.FlowAggregator.FlowDelta;
import com.netwatch.netwatch_api.traffic.FlowAggregator.FlowKey;
import com.netwatch.netwatch_api.traffic.FlowAggregator.StatDelta;

/**
 * Grava no banco o que o {@link FlowAggregator} acumulou: atualiza fluxos ativos, abre fluxos novos,
 * soma os totais por minuto e liga os IPs aos dispositivos do inventário.
 */
@Component
class TrafficRecorder {

    private static final Logger log = LoggerFactory.getLogger(TrafficRecorder.class);

    /** Intervalo mínimo entre duas observações do mesmo par IP/MAC enviadas ao inventário. */
    private static final Duration OBSERVATION_INTERVAL = Duration.ofMinutes(5);

    private final TrafficFlowRepository flows;
    private final TrafficMinuteStatRepository stats;
    private final CaptureSessionRepository sessions;
    private final DeviceRepository devices;
    private final DeviceService deviceService;
    private final MacVendorLookup vendors;
    private final CaptureProperties properties;

    TrafficRecorder(TrafficFlowRepository flows, TrafficMinuteStatRepository stats, CaptureSessionRepository sessions,
            DeviceRepository devices, DeviceService deviceService, MacVendorLookup vendors,
            CaptureProperties properties) {
        this.flows = flows;
        this.stats = stats;
        this.sessions = sessions;
        this.devices = devices;
        this.deviceService = deviceService;
        this.vendors = vendors;
        this.properties = properties;
    }

    /** Estado de uma sessão de captura entre uma gravação e outra. */
    static final class SessionState {
        final UUID sessionId;
        /** Sub-rede da interface capturada: só nela o MAC de origem é do próprio equipamento. */
        final Ipv4Network localSubnet;
        final Map<FlowKey, ActiveFlow> activeFlows = new HashMap<>();
        final Map<String, Instant> lastObservation = new ConcurrentHashMap<>();
        int newDevices;

        SessionState(UUID sessionId, Ipv4Network localSubnet) {
            this.sessionId = sessionId;
            this.localSubnet = localSubnet;
        }
    }

    record ActiveFlow(UUID id, Instant lastSeen) {
    }

    @Transactional
    public void persist(SessionState state, FlowAggregator.Drain drain, long packets, long bytes) {
        Map<String, Optional<UUID>> deviceCache = new HashMap<>();

        for (FlowDelta delta : drain.flows()) {
            FlowKey key = delta.key();
            TrafficFlow flow = continuing(state, delta);
            if (flow == null) {
                flow = new TrafficFlow(state.sessionId, key.protocol(), key.srcIp(), key.srcPort(), key.dstIp(),
                        key.dstPort(), delta.firstSeen());
            }
            flow.add(delta.packets(), delta.bytes(), delta.tcpFlags(), delta.lastSeen(), delta.srcMac(),
                    delta.dstMac());
            flow.describeConnection(delta.processName(), delta.connectionState());
            flow.linkDevices(
                    deviceFor(state, key.srcIp(), delta.srcMac(), deviceCache),
                    deviceFor(state, key.dstIp(), delta.dstMac(), deviceCache));
            flow = flows.save(flow);
            state.activeFlows.put(key, new ActiveFlow(flow.getId(), flow.getLastSeen()));
        }

        for (StatDelta delta : drain.stats()) {
            TrafficMinuteStat stat = stats.findByBucketStartAndProtocol(delta.bucket(), delta.protocol())
                    .orElseGet(() -> new TrafficMinuteStat(delta.bucket(), delta.protocol()));
            stat.add(delta.packets(), delta.bytes());
            stat.observeConnections(delta.connections());
            stats.save(stat);
        }

        sessions.findById(state.sessionId).ifPresent(s -> s.updateCounters(packets, bytes, state.newDevices));
        expireIdleFlows(state, drain);
    }

    /** O fluxo continua se o último pacote dele foi visto há menos que o tempo de inatividade. */
    private TrafficFlow continuing(SessionState state, FlowDelta delta) {
        ActiveFlow active = state.activeFlows.get(delta.key());
        if (active == null
                || Duration.between(active.lastSeen(), delta.firstSeen()).compareTo(properties.flowTimeout()) > 0) {
            return null;
        }
        return flows.findById(active.id()).orElse(null);
    }

    private void expireIdleFlows(SessionState state, FlowAggregator.Drain drain) {
        Instant newest = drain.flows().stream().map(FlowDelta::lastSeen).max(Instant::compareTo).orElse(null);
        if (newest != null) {
            Instant cutoff = newest.minus(properties.flowTimeout());
            state.activeFlows.values().removeIf(f -> f.lastSeen().isBefore(cutoff));
        }
    }

    /**
     * Liga o IP a um dispositivo. Na sub-rede local o MAC identifica o equipamento;
     * fora dela o MAC é do roteador, então só o IP serve.
     */
    private UUID deviceFor(SessionState state, String ip, String mac, Map<String, Optional<UUID>> cache) {
        boolean local = state.localSubnet != null && state.localSubnet.contains(ip);
        String cacheKey = ip + "|" + (local ? mac : "");
        return cache.computeIfAbsent(cacheKey, k -> {
            Optional<Device> device = Optional.empty();
            if (local && isUnicast(mac)) {
                device = devices.findByMacAddress(mac);
            }
            if (device.isEmpty()) {
                device = devices.findFirstByIpAddressOrderByLastSeenAtDesc(ip);
            }
            return device.map(Device::getId);
        }).orElse(null);
    }

    /**
     * Equipamentos da sub-rede local vistos no tráfego são registrados no inventário,
     * como faz a descoberta (atualiza "visto por último" ou cria um não reconhecido).
     * Roda fora da transação de {@link #persist}: cada observação tem a sua, e uma falha não desfaz os fluxos.
     */
    public void observeDevices(SessionState state, FlowAggregator.Drain drain) {
        if (state.localSubnet == null) {
            return;
        }
        for (FlowDelta delta : drain.flows()) {
            observe(state, delta.key().srcIp(), delta.srcMac(), delta.lastSeen());
            observe(state, delta.key().dstIp(), delta.dstMac(), delta.lastSeen());
        }
    }

    private void observe(SessionState state, String ip, String mac, Instant at) {
        if (!state.localSubnet.contains(ip) || !isUnicast(mac) || isNetworkOrBroadcast(state.localSubnet, ip)) {
            return;
        }
        String key = ip + "|" + mac;
        Instant previous = state.lastObservation.get(key);
        if (previous != null && Duration.between(previous, at).compareTo(OBSERVATION_INTERVAL) < 0) {
            return;
        }
        state.lastObservation.put(key, at);
        try {
            ObservationResult result = deviceService.registerObservation(
                    new DeviceObservation(ip, mac, null, vendors.lookup(mac).orElse(null)));
            if (result.created()) {
                state.newDevices++;
                log.info("Novo dispositivo visto no tráfego: {} ({})", ip, mac);
            }
        } catch (RuntimeException e) {
            log.debug("Não foi possível registrar {} ({}): {}", ip, mac, e.getMessage());
        }
    }

    /** MAC de um equipamento (não broadcast, multicast nem zerado). */
    static boolean isUnicast(String mac) {
        if (mac == null || mac.length() != 17 || mac.equals("00:00:00:00:00:00")) {
            return false;
        }
        int firstOctet = Integer.parseInt(mac.substring(0, 2), 16);
        return (firstOctet & 0x01) == 0;
    }

    private static boolean isNetworkOrBroadcast(Ipv4Network subnet, String ip) {
        if (subnet.prefixLength() >= 31) {
            return false;
        }
        int address = Ipv4Network.toInt(ip).orElse(0);
        int broadcast = subnet.network() | ~(-1 << (32 - subnet.prefixLength()));
        return address == subnet.network() || address == broadcast;
    }
}
