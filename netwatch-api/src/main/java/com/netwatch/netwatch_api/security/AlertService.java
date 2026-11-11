package com.netwatch.netwatch_api.security;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.activity.ActivityEvent;
import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.common.exception.ConflictException;
import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.common.exception.ResourceNotFoundException;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.dto.DeviceResponse;
import com.netwatch.netwatch_api.security.dto.AlertCountsResponse;
import com.netwatch.netwatch_api.security.dto.AlertDetailResponse;
import com.netwatch.netwatch_api.security.dto.AlertResponse;
import com.netwatch.netwatch_api.security.dto.BulkResultResponse;
import com.netwatch.netwatch_api.security.dto.SecuritySummaryResponse;

import jakarta.persistence.criteria.Predicate;

/** Criação (sem repetição), ciclo de vida e consulta dos alertas. */
@Service
@Transactional(readOnly = true)
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    static final Set<AlertStatus> ACTIVE = EnumSet.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED);
    static final Set<AlertStatus> CLOSED = EnumSet.of(AlertStatus.RESOLVED, AlertStatus.DISMISSED);

    private final SecurityAlertRepository alerts;
    private final AlertNoteRepository notes;
    private final DeviceRepository devices;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AlertService(SecurityAlertRepository alerts, AlertNoteRepository notes, DeviceRepository devices,
            ApplicationEventPublisher events, Clock clock) {
        this.alerts = alerts;
        this.notes = notes;
        this.devices = devices;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ criação (motor de segurança)

    /**
     * Registra uma detecção.
     * <ul>
     *   <li>Alerta ativo com a mesma {@code dedupKey}: soma uma ocorrência nele.</li>
     *   <li>Alerta descartado com "não alertar novamente": soma a ocorrência em silêncio.</li>
     *   <li>Senão, abre um alerta novo (e registra no histórico de atividades).</li>
     * </ul>
     *
     * @param severity gravidade desta detecção (nulo = a da regra)
     */
    @Transactional
    public SecurityAlert raise(RuleSettings rule, AlertSeverity severity, String dedupKey, UUID deviceId,
            String ipAddress, String title, String evidence) {
        AlertSeverity effective = severity != null ? severity : rule.severity();
        String key = rule.type().name() + ":" + dedupKey;
        Optional<SecurityAlert> existing = alerts.findFirstByDedupKeyAndStatusIn(key, ACTIVE)
                .or(() -> alerts.findFirstByDedupKeyAndSuppressedTrue(key));
        if (existing.isPresent()) {
            SecurityAlert alert = existing.get();
            alert.repeat(effective, evidence, clock.instant());
            return alert;
        }
        SecurityAlert alert = alerts.save(new SecurityAlert(rule.type(), effective, title, evidence, deviceId,
                ipAddress, key, clock.instant()));
        log.warn("Alerta {} [{}]: {}", rule.type(), effective, title);
        events.publishEvent(ActivityEvent.of(EventType.ALERT_RAISED, deviceId, ipAddress,
                        "Alerta " + severityLabel(effective) + ": " + title)
                .withDetails(rule.type().title()).withReference(alert.getId()));
        return alert;
    }

    // ------------------------------------------------------------------ ciclo de vida

    public enum BulkAction {
        ACKNOWLEDGE, RESOLVE, DISMISS
    }

    @Transactional
    public SecurityAlert acknowledge(UUID id, String note) {
        SecurityAlert alert = get(id);
        AlertStatus from = alert.acknowledge(clock.instant());
        recordStatus(alert, from, note);
        return alert;
    }

    @Transactional
    public SecurityAlert resolve(UUID id, String note) {
        SecurityAlert alert = get(id);
        doResolve(alert, note);
        return alert;
    }

    @Transactional
    public SecurityAlert dismiss(UUID id, DismissReason reason, String note, boolean suppress) {
        SecurityAlert alert = get(id);
        doDismiss(alert, reason, note, suppress);
        return alert;
    }

    @Transactional
    public SecurityAlert reopen(UUID id, String note) {
        SecurityAlert alert = get(id);
        AlertStatus from = alert.reopen(clock.instant());
        recordStatus(alert, from, note);
        return alert;
    }

    @Transactional
    public AlertNote comment(UUID id, String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("Escreva o comentário.");
        }
        SecurityAlert alert = get(id);
        return notes.save(AlertNote.comment(alert.getId(), clock.instant(), text.trim()));
    }

    /** Aplica a ação a cada alerta; os que não podem mudar (ex.: já resolvidos) são ignorados. */
    @Transactional
    public BulkResultResponse bulk(List<UUID> ids, BulkAction action, String note, DismissReason reason,
            boolean suppress) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidInputException("Selecione ao menos um alerta.");
        }
        if (action == BulkAction.DISMISS && reason == null) {
            throw new InvalidInputException("Informe o motivo do descarte.");
        }
        int changed = 0;
        int skipped = 0;
        for (UUID id : new HashSet<>(ids)) {
            Optional<SecurityAlert> found = alerts.findById(id);
            if (found.isEmpty()) {
                skipped++;
                continue;
            }
            try {
                SecurityAlert alert = found.get();
                switch (action) {
                    case ACKNOWLEDGE -> recordStatus(alert, alert.acknowledge(clock.instant()), note);
                    case RESOLVE -> doResolve(alert, note);
                    case DISMISS -> doDismiss(alert, reason, note, suppress);
                }
                changed++;
            } catch (ConflictException e) {
                skipped++;
            }
        }
        return new BulkResultResponse(changed, skipped);
    }

    /**
     * Resolve sozinho os alertas ativos de um dispositivo quando o problema deixa de existir
     * (ex.: dispositivo reconhecido, porta fechada).
     *
     * @param port se informado, só os alertas daquela porta (chave terminada em {@code :porta})
     */
    @Transactional
    public int autoResolve(UUID deviceId, Set<RuleType> rules, Integer port, String note) {
        if (deviceId == null) {
            return 0;
        }
        int count = 0;
        for (SecurityAlert alert : alerts.findByDeviceIdAndStatusIn(deviceId, ACTIVE)) {
            boolean samePort = port == null || alert.getDedupKey().endsWith(":" + port);
            if (rules.contains(alert.getRule()) && samePort) {
                doResolve(alert, note);
                count++;
            }
        }
        return count;
    }

    private void doResolve(SecurityAlert alert, String note) {
        AlertStatus from = alert.resolve(clock.instant(), note);
        recordStatus(alert, from, note);
        events.publishEvent(ActivityEvent.of(EventType.ALERT_RESOLVED, alert.getDeviceId(), alert.getIpAddress(),
                "Alerta resolvido: " + alert.getTitle()).withDetails(blankToNull(note)).withReference(alert.getId()));
    }

    private void doDismiss(SecurityAlert alert, DismissReason reason, String note, boolean suppress) {
        AlertStatus from = alert.dismiss(clock.instant(), reason, note, suppress);
        String text = reason.label() + (suppress ? " · não alertar novamente" : "")
                + (blankToNull(note) != null ? " — " + note.trim() : "");
        recordStatus(alert, from, text);
        events.publishEvent(ActivityEvent.of(EventType.ALERT_DISMISSED, alert.getDeviceId(), alert.getIpAddress(),
                "Alerta descartado: " + alert.getTitle()).withDetails(text).withReference(alert.getId()));
    }

    private void recordStatus(SecurityAlert alert, AlertStatus from, String text) {
        notes.save(AlertNote.statusChange(alert.getId(), clock.instant(), from, alert.getStatus(), blankToNull(text)));
    }

    // ------------------------------------------------------------------ consultas

    public SecurityAlert get(UUID id) {
        return alerts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta não encontrado: " + id));
    }

    public AlertDetailResponse detail(UUID id) {
        SecurityAlert alert = get(id);
        return new AlertDetailResponse(AlertResponse.from(alert, deviceNames(List.of(alert))),
                notes.findByAlertIdOrderByCreatedAtAsc(id).stream().map(AlertDetailResponse.Note::from).toList());
    }

    public AlertResponse response(SecurityAlert alert) {
        return AlertResponse.from(alert, deviceNames(List.of(alert)));
    }

    /**
     * @param state {@code active} (abertos e reconhecidos) ou {@code closed} (resolvidos e descartados)
     * @param q     busca no título, na evidência e no IP
     */
    public record AlertFilter(AlertStatus status, String state, AlertSeverity severity, RuleType rule,
            UUID deviceId, String q) {
    }

    public Page<AlertResponse> search(AlertFilter f, Pageable pageable) {
        Specification<SecurityAlert> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (f.status() != null) {
                where.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.state() != null && !f.state().isBlank()) {
                Set<AlertStatus> set = switch (f.state().trim().toLowerCase(Locale.ROOT)) {
                    case "active" -> ACTIVE;
                    case "closed" -> CLOSED;
                    default -> throw new InvalidInputException("Situação inválida: " + f.state()
                            + ". Use active ou closed.");
                };
                where.add(root.get("status").in(set));
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
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("evidence"), "")), like),
                        cb.like(cb.coalesce(root.get("ipAddress"), ""), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<SecurityAlert> page = alerts.findAll(spec, pageable);
        Map<UUID, String> names = deviceNames(page.getContent());
        return page.map(a -> AlertResponse.from(a, names));
    }

    /** Totais por situação e ativos por gravidade (para o contador do menu e os avisos). */
    public AlertCountsResponse counts() {
        Map<AlertStatus, Long> byStatus = new EnumMap<>(AlertStatus.class);
        alerts.countByStatus().forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
        Map<AlertSeverity, Long> activeBySeverity = new EnumMap<>(AlertSeverity.class);
        alerts.countActive(ACTIVE).forEach(c -> activeBySeverity.merge(c.getSeverity(), c.getTotal(), Long::sum));
        Instant lastRaised = alerts.findFirstByOrderByFirstSeenAtDesc().map(SecurityAlert::getFirstSeenAt).orElse(null);
        return new AlertCountsResponse(
                byStatus.getOrDefault(AlertStatus.OPEN, 0L) + byStatus.getOrDefault(AlertStatus.ACKNOWLEDGED, 0L),
                byStatus.getOrDefault(AlertStatus.OPEN, 0L),
                byStatus.getOrDefault(AlertStatus.ACKNOWLEDGED, 0L),
                byStatus.getOrDefault(AlertStatus.RESOLVED, 0L),
                byStatus.getOrDefault(AlertStatus.DISMISSED, 0L),
                activeBySeverity.getOrDefault(AlertSeverity.CRITICAL, 0L),
                activeBySeverity.getOrDefault(AlertSeverity.HIGH, 0L),
                activeBySeverity.getOrDefault(AlertSeverity.MEDIUM, 0L),
                activeBySeverity.getOrDefault(AlertSeverity.LOW, 0L),
                lastRaised);
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

    private static String severityLabel(AlertSeverity severity) {
        return switch (severity) {
            case LOW -> "baixo";
            case MEDIUM -> "médio";
            case HIGH -> "alto";
            case CRITICAL -> "crítico";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
