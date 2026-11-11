package com.netwatch.netwatch_api.security;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.dto.DeviceResponse;
import com.netwatch.netwatch_api.security.dto.AlertResponse;
import com.netwatch.netwatch_api.security.dto.SecuritySummaryResponse;

import jakarta.persistence.criteria.Predicate;

/** Criação (sem repetição) e consulta dos alertas. */
@Service
@Transactional(readOnly = true)
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    static final Set<AlertStatus> ACTIVE = EnumSet.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED);

    private final SecurityAlertRepository alerts;
    private final DeviceRepository devices;
    private final Clock clock;

    public AlertService(SecurityAlertRepository alerts, DeviceRepository devices, Clock clock) {
        this.alerts = alerts;
        this.devices = devices;
        this.clock = clock;
    }

    /**
     * Registra uma detecção. Se já existe alerta ativo com a mesma {@code dedupKey}, soma uma ocorrência
     * nele; senão, abre um novo.
     *
     * @param severity gravidade desta detecção (nulo = a da regra)
     * @return o alerta criado ou atualizado
     */
    @Transactional
    public SecurityAlert raise(RuleSettings rule, AlertSeverity severity, String dedupKey, UUID deviceId,
            String ipAddress, String title, String evidence) {
        AlertSeverity effective = severity != null ? severity : rule.severity();
        String key = rule.type().name() + ":" + dedupKey;
        Optional<SecurityAlert> existing = alerts.findFirstByDedupKeyAndStatusIn(key, ACTIVE);
        if (existing.isPresent()) {
            SecurityAlert alert = existing.get();
            alert.repeat(effective, evidence, clock.instant());
            return alert;
        }
        SecurityAlert alert = alerts.save(new SecurityAlert(rule.type(), effective, title, evidence, deviceId,
                ipAddress, key, clock.instant()));
        log.warn("Alerta {} [{}]: {}", rule.type(), effective, title);
        return alert;
    }

    public record AlertFilter(AlertStatus status, AlertSeverity severity, RuleType rule, UUID deviceId) {
    }

    public Page<AlertResponse> search(AlertFilter f, Pageable pageable) {
        Specification<SecurityAlert> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (f.status() != null) {
                where.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.severity() != null) {
                where.add(cb.equal(root.get("severity"), f.severity()));
            }
            if (f.rule() != null) {
                where.add(cb.equal(root.get("rule"), f.rule()));
            }
            if (f.deviceId() != null) {
                where.add(cb.equal(root.get("deviceId"), f.deviceId()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<SecurityAlert> page = alerts.findAll(spec, pageable);
        Map<UUID, String> names = deviceNames(page.getContent());
        return page.map(a -> AlertResponse.from(a, names));
    }

    public SecuritySummaryResponse summary(List<RuleSettings> rules) {
        Map<AlertSeverity, Long> bySeverity = new EnumMap<>(AlertSeverity.class);
        Map<RuleType, Long> byRule = new EnumMap<>(RuleType.class);
        for (SecurityAlertRepository.RuleCount c : alerts.countActive(ACTIVE)) {
            bySeverity.merge(c.getSeverity(), c.getTotal(), Long::sum);
            byRule.merge(c.getRule(), c.getTotal(), Long::sum);
        }
        List<SecurityAlert> recent = alerts.findByStatusInOrderByLastSeenAtDesc(ACTIVE, Limit.of(5));
        Map<UUID, String> names = deviceNames(recent);
        long enabled = rules.stream().filter(RuleSettings::enabled).count();
        return new SecuritySummaryResponse(
                bySeverity.values().stream().mapToLong(Long::longValue).sum(),
                bySeverity.getOrDefault(AlertSeverity.CRITICAL, 0L),
                bySeverity.getOrDefault(AlertSeverity.HIGH, 0L),
                bySeverity.getOrDefault(AlertSeverity.MEDIUM, 0L),
                bySeverity.getOrDefault(AlertSeverity.LOW, 0L),
                enabled, rules.size(),
                rules.stream().map(r -> new SecuritySummaryResponse.RuleCount(r.type(), r.type().title(),
                        byRule.getOrDefault(r.type(), 0L))).toList(),
                recent.stream().map(a -> AlertResponse.from(a, names)).toList());
    }

    /** Alertas ativos por regra (para a tela de regras). */
    public Map<RuleType, Long> activeByRule() {
        Map<RuleType, Long> byRule = new EnumMap<>(RuleType.class);
        alerts.countActive(ACTIVE).forEach(c -> byRule.merge(c.getRule(), c.getTotal(), Long::sum));
        return byRule;
    }

    private Map<UUID, String> deviceNames(List<SecurityAlert> list) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(a -> {
            if (a.getDeviceId() != null) {
                ids.add(a.getDeviceId());
            }
        });
        if (ids.isEmpty()) {
            return Map.of();
        }
        return devices.findAllById(ids).stream()
                .collect(Collectors.toMap(Device::getId, DeviceResponse::displayName));
    }
}
