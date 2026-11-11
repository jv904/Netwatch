package com.netwatch.netwatch_api.traffic;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.device.dto.DeviceResponse;
import com.netwatch.netwatch_api.discovery.Ipv4Network;
import com.netwatch.netwatch_api.traffic.TrafficFlowRepository.IpTotal;
import com.netwatch.netwatch_api.traffic.dto.DeviceTrafficResponse;
import com.netwatch.netwatch_api.traffic.dto.FlowResponse;
import com.netwatch.netwatch_api.traffic.dto.TrafficSummaryResponse;
import com.netwatch.netwatch_api.traffic.dto.TrafficSummaryResponse.HostShare;
import com.netwatch.netwatch_api.traffic.dto.TrafficSummaryResponse.Point;
import com.netwatch.netwatch_api.traffic.dto.TrafficSummaryResponse.ProtocolShare;
import com.netwatch.netwatch_api.traffic.dto.TrafficSummaryResponse.ServiceShare;
import com.netwatch.netwatch_api.traffic.dto.TrafficSummaryResponse.Totals;

/** Consultas sobre o tráfego já gravado. */
@Service
@Transactional(readOnly = true)
public class TrafficQueryService {

    private static final int TOP_HOSTS = 10;
    private static final int TOP_SERVICES = 10;
    private static final int TOP_PEERS = 5;
    private static final int RECENT_FLOWS = 10;

    private final TrafficFlowRepository flows;
    private final TrafficMinuteStatRepository stats;
    private final DeviceRepository devices;
    private final DeviceService deviceService;
    private final CaptureService captureService;
    private final Clock clock;

    public TrafficQueryService(TrafficFlowRepository flows, TrafficMinuteStatRepository stats,
            DeviceRepository devices, DeviceService deviceService, CaptureService captureService, Clock clock) {
        this.flows = flows;
        this.stats = stats;
        this.devices = devices;
        this.deviceService = deviceService;
        this.captureService = captureService;
        this.clock = clock;
    }

    /**
     * Filtros de fluxos; todos opcionais.
     *
     * @param ip   origem ou destino
     * @param port porta de origem ou destino
     */
    public record FlowFilter(String ip, UUID deviceId, TrafficProtocol protocol, Integer port, TrafficWindow window) {
    }

    public Page<FlowResponse> flows(FlowFilter filter, Pageable pageable) {
        Page<TrafficFlow> page = flows.findAll(matching(filter), pageable);
        Map<UUID, String> names = deviceNames(page.getContent());
        return page.map(f -> FlowResponse.from(f, names));
    }

    private Specification<TrafficFlow> matching(FlowFilter f) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> where = new ArrayList<>();
            if (f.window() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("lastSeen"), since(f.window())));
            }
            if (f.ip() != null && !f.ip().isBlank()) {
                String ip = f.ip().trim();
                where.add(cb.or(cb.equal(root.get("srcIp"), ip), cb.equal(root.get("dstIp"), ip)));
            }
            if (f.deviceId() != null) {
                where.add(cb.or(cb.equal(root.get("srcDeviceId"), f.deviceId()),
                        cb.equal(root.get("dstDeviceId"), f.deviceId())));
            }
            if (f.protocol() != null) {
                where.add(cb.equal(root.get("protocol"), f.protocol()));
            }
            if (f.port() != null) {
                where.add(cb.or(cb.equal(root.get("srcPort"), f.port()), cb.equal(root.get("dstPort"), f.port())));
            }
            return cb.and(where.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public TrafficSummaryResponse summary(TrafficWindow window) {
        Instant now = clock.instant();
        Instant since = since(window);
        List<Ipv4Network> networks = captureService.authorizedNetworks();

        // Série temporal e protocolos, a partir dos totais por minuto.
        long bucketMs = window.bucket().toMillis();
        long first = Math.floorDiv(since.toEpochMilli(), bucketMs) * bucketMs;
        long last = Math.floorDiv(now.toEpochMilli(), bucketMs) * bucketMs;
        Map<Long, long[]> buckets = new LinkedHashMap<>();
        for (long t = first; t <= last; t += bucketMs) {
            buckets.put(t, new long[3]);
        }
        Map<TrafficProtocol, long[]> byProtocol = new EnumMap<>(TrafficProtocol.class);
        // Conexões simultâneas por minuto (somando os protocolos); cada intervalo do gráfico mostra o pico.
        Map<Instant, Long> connectionsPerMinute = new HashMap<>();
        long totalPackets = 0;
        long totalBytes = 0;
        for (TrafficMinuteStat s : stats.findByBucketStartGreaterThanEqualOrderByBucketStart(Instant.ofEpochMilli(first))) {
            long key = Math.floorDiv(s.getBucketStart().toEpochMilli(), bucketMs) * bucketMs;
            long[] point = buckets.computeIfAbsent(key, k -> new long[3]);
            point[0] += s.getPackets();
            point[1] += s.getBytes();
            long[] proto = byProtocol.computeIfAbsent(s.getProtocol(), p -> new long[2]);
            proto[0] += s.getPackets();
            proto[1] += s.getBytes();
            totalPackets += s.getPackets();
            totalBytes += s.getBytes();
            connectionsPerMinute.merge(s.getBucketStart(), s.getConnections(), Long::sum);
        }
        long peakConnections = 0;
        for (Map.Entry<Instant, Long> e : connectionsPerMinute.entrySet()) {
            long key = Math.floorDiv(e.getKey().toEpochMilli(), bucketMs) * bucketMs;
            long[] point = buckets.computeIfAbsent(key, k -> new long[3]);
            point[2] = Math.max(point[2], e.getValue());
            peakConnections = Math.max(peakConnections, e.getValue());
        }
        List<Point> series = buckets.entrySet().stream()
                .map(e -> new Point(Instant.ofEpochMilli(e.getKey()), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2]))
                .toList();
        List<ProtocolShare> protocols = byProtocol.entrySet().stream()
                .map(e -> new ProtocolShare(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(ProtocolShare::bytes).reversed())
                .toList();

        // Hosts: enviado (como origem) + recebido (como destino). Sem volume (tabela de conexões),
        // o desempate é pelo número de fluxos.
        Map<String, long[]> hosts = new HashMap<>();
        for (IpTotal t : flows.totalsBySource(since)) {
            long[] h = hosts.computeIfAbsent(t.getIp(), k -> new long[4]);
            h[0] += t.getBytes();
            h[2] += t.getPackets();
            h[3] += t.getFlows();
        }
        for (IpTotal t : flows.totalsByDestination(since)) {
            long[] h = hosts.computeIfAbsent(t.getIp(), k -> new long[4]);
            h[1] += t.getBytes();
            h[2] += t.getPackets();
            h[3] += t.getFlows();
        }
        long localHosts = hosts.keySet().stream().filter(ip -> isLocal(networks, ip)).count();
        List<HostShare> topHosts = hosts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0] + e.getValue()[1])
                        .thenComparingLong(e -> e.getValue()[3])
                        .reversed())
                .limit(TOP_HOSTS)
                .map(e -> {
                    Optional<Device> device = deviceByIp(networks, e.getKey());
                    return new HostShare(e.getKey(), device.map(Device::getId).orElse(null),
                            device.map(DeviceResponse::displayName).orElse(null), isLocal(networks, e.getKey()),
                            e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]);
                })
                .toList();

        List<ServiceShare> services = flows.topServices(since, Limit.of(TOP_SERVICES)).stream()
                .map(s -> new ServiceShare(s.getProtocol(), s.getPort(),
                        TrafficServices.nameOf(s.getProtocol(), s.getPort()), s.getBytes(), s.getPackets(),
                        s.getFlows()))
                .toList();

        Totals totals = new Totals(totalPackets, totalBytes, flows.countByLastSeenGreaterThanEqual(since), localHosts,
                peakConnections);
        return new TrafficSummaryResponse(window.code(), since, now, window.bucket().toSeconds(), totals, series,
                protocols, topHosts, services);
    }

    public DeviceTrafficResponse deviceTraffic(UUID deviceId, TrafficWindow window) {
        deviceService.get(deviceId);
        Instant since = since(window);
        List<Ipv4Network> networks = captureService.authorizedNetworks();

        List<IpTotal> sent = flows.sentByDevice(deviceId, since);
        List<IpTotal> received = flows.receivedByDevice(deviceId, since);
        Map<String, long[]> peers = new HashMap<>();
        sent.forEach(t -> add(peers, t));
        received.forEach(t -> add(peers, t));
        List<DeviceTrafficResponse.Peer> topPeers = peers.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0])
                        .thenComparingLong(e -> e.getValue()[2])
                        .reversed())
                .limit(TOP_PEERS)
                .map(e -> {
                    Optional<Device> device = deviceByIp(networks, e.getKey());
                    return new DeviceTrafficResponse.Peer(e.getKey(), device.map(Device::getId).orElse(null),
                            device.map(DeviceResponse::displayName).orElse(null), e.getValue()[0], e.getValue()[1],
                            e.getValue()[2]);
                })
                .toList();

        Page<FlowResponse> recent = flows(new FlowFilter(null, deviceId, null, null, window),
                PageRequest.of(0, RECENT_FLOWS, Sort.by(Sort.Direction.DESC, "lastSeen")));

        return new DeviceTrafficResponse(window.code(),
                sent.stream().mapToLong(IpTotal::getBytes).sum(),
                received.stream().mapToLong(IpTotal::getBytes).sum(),
                sent.stream().mapToLong(IpTotal::getPackets).sum(),
                received.stream().mapToLong(IpTotal::getPackets).sum(),
                sent.stream().mapToLong(IpTotal::getFlows).sum() + received.stream().mapToLong(IpTotal::getFlows).sum(),
                topPeers, recent.getContent());
    }

    private static void add(Map<String, long[]> peers, IpTotal t) {
        long[] p = peers.computeIfAbsent(t.getIp(), k -> new long[3]);
        p[0] += t.getBytes();
        p[1] += t.getPackets();
        p[2] += t.getFlows();
    }

    private Instant since(TrafficWindow window) {
        return clock.instant().minus(window.length());
    }

    private static boolean isLocal(List<Ipv4Network> networks, String ip) {
        return networks.stream().anyMatch(n -> n.contains(ip));
    }

    /** Só IPs das redes autorizadas são procurados no inventário. */
    private Optional<Device> deviceByIp(List<Ipv4Network> networks, String ip) {
        return isLocal(networks, ip) ? devices.findFirstByIpAddressOrderByLastSeenAtDesc(ip) : Optional.empty();
    }

    private Map<UUID, String> deviceNames(List<TrafficFlow> content) {
        Set<UUID> ids = new HashSet<>();
        for (TrafficFlow f : content) {
            if (f.getSrcDeviceId() != null) {
                ids.add(f.getSrcDeviceId());
            }
            if (f.getDstDeviceId() != null) {
                ids.add(f.getDstDeviceId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return devices.findAllById(ids).stream()
                .collect(Collectors.toMap(Device::getId, DeviceResponse::displayName));
    }
}
