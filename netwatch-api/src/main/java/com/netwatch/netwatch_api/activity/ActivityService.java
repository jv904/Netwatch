package com.netwatch.netwatch_api.activity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.activity.dto.ActivitySummaryResponse;
import com.netwatch.netwatch_api.activity.dto.ActivitySummaryResponse.DeviceCount;
import com.netwatch.netwatch_api.activity.dto.ActivitySummaryResponse.HourCount;
import com.netwatch.netwatch_api.activity.dto.ActivitySummaryResponse.Point;
import com.netwatch.netwatch_api.activity.dto.ActivitySummaryResponse.Totals;
import com.netwatch.netwatch_api.activity.dto.ActivitySummaryResponse.TypeCount;
import com.netwatch.netwatch_api.activity.dto.EventResponse;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.dto.DeviceResponse;

import jakarta.persistence.criteria.Predicate;

/** Consulta e análise do histórico de atividades. */
@Service
@Transactional(readOnly = true)
public class ActivityService {

    private static final int TOP_DEVICES = 8;

    private final NetworkEventRepository events;
    private final DeviceRepository devices;
    private final com.netwatch.netwatch_api.settings.SettingsService settings;
    private final Clock clock;

    public ActivityService(NetworkEventRepository events, DeviceRepository devices,
            com.netwatch.netwatch_api.settings.SettingsService settings, Clock clock) {
        this.events = events;
        this.devices = devices;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Filtros do histórico; todos opcionais.
     *
     * @param q busca no título, nos detalhes e no IP
     */
    public record EventFilter(EventType type, EventCategory category, EventSeverity severity, UUID deviceId,
            String q, ActivityWindow window) {
    }

    public Page<EventResponse> search(EventFilter filter, Pageable pageable) {
        Page<NetworkEvent> page = events.findAll(matching(filter), pageable);
        Map<UUID, String> names = deviceNames(page.getContent().stream().map(NetworkEvent::getDeviceId).toList());
        return page.map(e -> EventResponse.from(e, names));
    }

    private Specification<NetworkEvent> matching(EventFilter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (f.window() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), clock.instant().minus(f.window().length())));
            }
            if (f.type() != null) {
                where.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.category() != null) {
                where.add(cb.equal(root.get("category"), f.category()));
            }
            if (f.severity() != null) {
                where.add(cb.equal(root.get("severity"), f.severity()));
            }
            if (f.deviceId() != null) {
                where.add(cb.equal(root.get("deviceId"), f.deviceId()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("details"), "")), like),
                        cb.like(cb.coalesce(root.get("ipAddress"), ""), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    public ActivitySummaryResponse summary(ActivityWindow window) {
        Instant now = clock.instant();
        Instant since = now.minus(window.length());
        ZoneId zone = ZoneId.systemDefault();
        List<NetworkEvent> list = events.findByOccurredAtGreaterThanEqual(since);

        long bucketMs = window.bucket().toMillis();
        long first = Math.floorDiv(since.toEpochMilli(), bucketMs) * bucketMs;
        long last = Math.floorDiv(now.toEpochMilli(), bucketMs) * bucketMs;
        Map<Long, long[]> buckets = new LinkedHashMap<>();
        for (long t = first; t <= last; t += bucketMs) {
            buckets.put(t, new long[2]);
        }
        Map<EventType, Long> byType = new EnumMap<>(EventType.class);
        Map<EventSeverity, Long> bySeverity = new EnumMap<>(EventSeverity.class);
        long[] byHour = new long[24];
        Map<UUID, long[]> byDevice = new HashMap<>();
        Map<UUID, String> lastIp = new HashMap<>();

        for (NetworkEvent e : list) {
            long key = Math.floorDiv(e.getOccurredAt().toEpochMilli(), bucketMs) * bucketMs;
            long[] point = buckets.computeIfAbsent(key, k -> new long[2]);
            point[0]++;
            boolean warning = e.getSeverity() == EventSeverity.WARNING;
            if (warning) {
                point[1]++;
            }
            byType.merge(e.getType(), 1L, Long::sum);
            bySeverity.merge(e.getSeverity(), 1L, Long::sum);
            byHour[e.getOccurredAt().atZone(zone).getHour()]++;
            if (e.getDeviceId() != null) {
                long[] d = byDevice.computeIfAbsent(e.getDeviceId(), k -> new long[2]);
                d[0]++;
                if (warning) {
                    d[1]++;
                }
                lastIp.putIfAbsent(e.getDeviceId(), e.getIpAddress());
            }
        }

        List<Point> series = buckets.entrySet().stream()
                .map(b -> new Point(Instant.ofEpochMilli(b.getKey()), b.getValue()[0], b.getValue()[1]))
                .toList();
        List<TypeCount> types = byType.entrySet().stream()
                .map(t -> new TypeCount(t.getKey(), t.getKey().category(), t.getKey().severity(), t.getValue()))
                .sorted(Comparator.comparingLong(TypeCount::count).reversed())
                .toList();
        List<HourCount> hours = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            hours.add(new HourCount(h, byHour[h]));
        }
        List<Map.Entry<UUID, long[]>> top = byDevice.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, long[]> d) -> d.getValue()[1])
                        .thenComparingLong(d -> d.getValue()[0])
                        .reversed())
                .limit(TOP_DEVICES)
                .toList();
        Map<UUID, Device> topDevices = devices.findAllById(top.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(Device::getId, d -> d));
        List<DeviceCount> deviceCounts = top.stream()
                .filter(d -> topDevices.containsKey(d.getKey()))
                .map(d -> {
                    Device device = topDevices.get(d.getKey());
                    return new DeviceCount(d.getKey(), DeviceResponse.displayName(device), device.getIpAddress(),
                            d.getValue()[0], d.getValue()[1]);
                })
                .toList();

        Totals totals = new Totals(list.size(),
                bySeverity.getOrDefault(EventSeverity.WARNING, 0L),
                bySeverity.getOrDefault(EventSeverity.NOTICE, 0L),
                bySeverity.getOrDefault(EventSeverity.INFO, 0L),
                byType.getOrDefault(EventType.DEVICE_DISCOVERED, 0L),
                byType.getOrDefault(EventType.PORT_OPENED, 0L),
                byType.getOrDefault(EventType.TRAFFIC_NEW_SERVICE, 0L));
        return new ActivitySummaryResponse(window.code(), since, now, window.bucket().toSeconds(), zone.getId(),
                totals, series, types, hours, deviceCounts);
    }

    private Map<UUID, String> deviceNames(List<UUID> ids) {
        Set<UUID> unique = new HashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) {
            return Map.of();
        }
        return devices.findAllById(unique).stream()
                .collect(Collectors.toMap(Device::getId, DeviceResponse::displayName));
    }

    /** Apaga eventos mais antigos que {@code netwatch.activity.retention-days}. */
    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT6H")
    @Transactional
    public void purge() {
        int days = settings.integer(com.netwatch.netwatch_api.settings.SettingKey.ACTIVITY_RETENTION_DAYS);
        events.deleteOlderThan(clock.instant().minus(java.time.Duration.ofDays(Math.max(1, days))));
    }
}
