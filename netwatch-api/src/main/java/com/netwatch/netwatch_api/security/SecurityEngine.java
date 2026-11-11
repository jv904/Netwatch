package com.netwatch.netwatch_api.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.netwatch.netwatch_api.activity.ActivityEvent;
import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks.AuthorizedNetwork;
import com.netwatch.netwatch_api.discovery.Ipv4Network;
import com.netwatch.netwatch_api.ports.DevicePort;
import com.netwatch.netwatch_api.ports.DevicePortRepository;
import com.netwatch.netwatch_api.traffic.TrafficFlow;
import com.netwatch.netwatch_api.traffic.TrafficFlowRepository;
import com.netwatch.netwatch_api.traffic.TrafficProtocol;
import com.netwatch.netwatch_api.traffic.TrafficServices;

/**
 * Motor de segurança: aplica as regras configuradas e gera alertas.
 * <ul>
 *   <li>Regras de eventos: rodam quando um {@link ActivityEvent} é publicado, depois que a operação que o
 *   gerou foi gravada (uma falha aqui nunca desfaz a coleta).</li>
 *   <li>Regras de tráfego: rodam a cada minuto sobre os fluxos recentes.</li>
 * </ul>
 */
@Component
public class SecurityEngine {

    private static final Logger log = LoggerFactory.getLogger(SecurityEngine.class);

    /** Fluxos analisados pelas regras de "fluxo novo" (descartados da memória depois de 30 min). */
    private static final Duration FLOW_MEMORY = Duration.ofMinutes(30);
    /** Fluxos gravados há até 15 min ainda são considerados novos (a captura grava a cada ~10 s). */
    private static final Duration NEW_FLOW_LOOKBACK = Duration.ofMinutes(15);

    private final SecurityRuleService rules;
    private final AlertService alerts;
    private final DeviceRepository devices;
    private final DevicePortRepository devicePorts;
    private final TrafficFlowRepository flows;
    private final AuthorizedNetworks authorizedNetworks;
    private final TransactionTemplate newTransaction;
    private final Clock clock;
    private final Map<UUID, Instant> evaluatedFlows = new ConcurrentHashMap<>();

    public SecurityEngine(SecurityRuleService rules, AlertService alerts, DeviceRepository devices,
            DevicePortRepository devicePorts, TrafficFlowRepository flows, AuthorizedNetworks authorizedNetworks,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.rules = rules;
        this.alerts = alerts;
        this.devices = devices;
        this.devicePorts = devicePorts;
        this.flows = flows;
        this.authorizedNetworks = authorizedNetworks;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ regras de eventos

    @TransactionalEventListener(fallbackExecution = true)
    public void onEvent(ActivityEvent event) {
        try {
            newTransaction.executeWithoutResult(status -> evaluate(event));
        } catch (RuntimeException e) {
            log.warn("Motor de segurança: falha ao avaliar {}: {}", event.type(), e.getMessage());
        }
    }

    void evaluate(ActivityEvent e) {
        switch (e.type()) {
            case DEVICE_DISCOVERED -> {
                unknownDevice(e);
                offHours(e);
            }
            case PORT_OPENED -> {
                newOpenPort(e);
                riskyService(e.deviceId(), e.ipAddress(), e.port(), "passou a aceitar conexões");
                offHours(e);
            }
            case PORT_SCAN_COMPLETED -> exposedRiskyServices(e);
            case TRAFFIC_NEW_SERVICE -> {
                riskyService(e.deviceId(), e.ipAddress(), e.port(), "visto em uso no tráfego");
                offHours(e);
            }
            case DEVICE_IP_CONFLICT -> ipConflict(e);
            default -> {
                // Os demais eventos não disparam regras.
            }
        }
    }

    private void unknownDevice(ActivityEvent e) {
        RuleSettings rule = rules.settings(RuleType.UNKNOWN_DEVICE);
        Optional<Device> device = device(e.deviceId());
        if (!rule.enabled() || device.isEmpty() || device.get().isRecognized()) {
            return;
        }
        Device d = device.get();
        String vendor = d.getVendor() != null ? " (" + d.getVendor() + ")" : "";
        alerts.raise(rule, null, d.getId().toString(), d.getId(), d.getIpAddress(),
                "Dispositivo desconhecido na rede: " + d.getIpAddress() + vendor, evidence(e));
    }

    private void newOpenPort(ActivityEvent e) {
        RuleSettings rule = rules.settings(RuleType.NEW_OPEN_PORT);
        Optional<Device> device = device(e.deviceId());
        if (!rule.enabled() || e.port() == null || device.isEmpty() || !device.get().isRecognized()) {
            return;
        }
        alerts.raise(rule, null, e.deviceId() + ":" + e.port(), e.deviceId(), e.ipAddress(),
                "Porta nova " + describePort(e.port()) + " em " + label(device.get()), evidence(e));
    }

    /** Ao fim de uma verificação, todo serviço de risco aberto no dispositivo vira (ou soma em) um alerta. */
    private void exposedRiskyServices(ActivityEvent e) {
        if (e.deviceId() == null) {
            return;
        }
        for (DevicePort port : devicePorts.findByDeviceIdOrderByPort(e.deviceId())) {
            if (port.getState() == PortState.OPEN) {
                riskyService(e.deviceId(), e.ipAddress(), port.getPort(), "aceita conexões");
            }
        }
    }

    private void riskyService(UUID deviceId, String ip, Integer port, String how) {
        RuleSettings rule = rules.settings(RuleType.RISKY_SERVICE);
        if (!rule.enabled() || port == null || !rule.ports("ports").contains(port)) {
            return;
        }
        String target = device(deviceId).map(SecurityEngine::label).orElse(ip);
        alerts.raise(rule, null, (deviceId != null ? deviceId.toString() : ip) + ":" + port, deviceId, ip,
                "Serviço de risco exposto: " + describePort(port) + " em " + target,
                "A porta " + port + " " + how + ".");
    }

    private void ipConflict(ActivityEvent e) {
        RuleSettings rule = rules.settings(RuleType.IP_CONFLICT);
        if (!rule.enabled() || e.ipAddress() == null) {
            return;
        }
        boolean critical = isCriticalIp(e.ipAddress(), rule.ips("criticalIps"));
        alerts.raise(rule, critical ? AlertSeverity.CRITICAL : null, e.ipAddress(), e.deviceId(), e.ipAddress(),
                "Conflito de IP em " + e.ipAddress() + (critical ? " (equipamento crítico)" : ""), evidence(e));
    }

    /** IPs críticos configurados ou, sem configuração, os finais .1 e .254 (roteadores, em geral). */
    static boolean isCriticalIp(String ip, List<String> configured) {
        if (!configured.isEmpty()) {
            return configured.contains(ip);
        }
        return ip.endsWith(".1") || ip.endsWith(".254");
    }

    private void offHours(ActivityEvent e) {
        RuleSettings rule = rules.settings(RuleType.OFF_HOURS);
        if (!rule.enabled()) {
            return;
        }
        ZonedDateTime now = clock.instant().atZone(ZoneId.systemDefault());
        if (!isOffHours(now.getHour(), rule.intValue("start"), rule.intValue("end"))) {
            return;
        }
        String subject = e.deviceId() != null ? e.deviceId().toString() : String.valueOf(e.ipAddress());
        // Um alerta por tipo de evento, equipamento e noite (a data é a do início do período).
        String night = (now.getHour() < rule.intValue("end") ? now.minusDays(1) : now).toLocalDate().toString();
        alerts.raise(rule, null, e.type() + ":" + subject + ":" + night, e.deviceId(), e.ipAddress(),
                "Fora do horário: " + e.title(), evidence(e));
    }

    /** O período fora do expediente pode cruzar a meia-noite (ex.: das 22h às 6h). */
    static boolean isOffHours(int hour, int start, int end) {
        if (start == end) {
            return false;
        }
        return start < end ? hour >= start && hour < end : hour >= start || hour < end;
    }

    // ------------------------------------------------------------------ regras de tráfego

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void scheduledTrafficCheck() {
        try {
            evaluateTraffic();
        } catch (RuntimeException e) {
            log.warn("Motor de segurança: falha ao analisar o tráfego: {}", e.getMessage());
        }
    }

    /** Analisa os fluxos recentes (varredura) e os fluxos novos (portas suspeitas, acesso externo). */
    public void evaluateTraffic() {
        newTransaction.executeWithoutResult(status -> {
            List<Ipv4Network> networks = authorizedNetworks.list().stream().map(AuthorizedNetwork::network).toList();
            Instant now = clock.instant();
            portScan(networks, now);
            Instant cutoff = now.minus(FLOW_MEMORY);
            evaluatedFlows.values().removeIf(at -> at.isBefore(cutoff));
            for (TrafficFlow flow : flows.findByFirstSeenGreaterThanEqual(now.minus(NEW_FLOW_LOOKBACK))) {
                if (evaluatedFlows.putIfAbsent(flow.getId(), now) == null) {
                    suspiciousPort(flow);
                    externalAdminAccess(flow, networks);
                }
            }
        });
    }

    private void portScan(List<Ipv4Network> networks, Instant now) {
        RuleSettings rule = rules.settings(RuleType.PORT_SCAN);
        if (!rule.enabled()) {
            return;
        }
        int portThreshold = rule.intValue("portThreshold");
        int hostThreshold = rule.intValue("hostThreshold");
        Instant since = now.minus(Duration.ofMinutes(rule.intValue("windowMinutes")));

        Map<String, Set<Integer>> portsByPair = new HashMap<>();
        Map<String, Set<String>> localHostsBySource = new HashMap<>();
        Map<String, UUID> deviceBySource = new HashMap<>();
        for (TrafficFlow f : flows.findByLastSeenGreaterThanEqual(since)) {
            if (f.getProtocol() != TrafficProtocol.TCP && f.getProtocol() != TrafficProtocol.UDP) {
                continue;
            }
            if (f.getDstPort() != null) {
                portsByPair.computeIfAbsent(f.getSrcIp() + ">" + f.getDstIp(), k -> new HashSet<>()).add(f.getDstPort());
            }
            if (inScope(networks, f.getDstIp())) {
                localHostsBySource.computeIfAbsent(f.getSrcIp(), k -> new HashSet<>()).add(f.getDstIp());
            }
            if (f.getSrcDeviceId() != null) {
                deviceBySource.put(f.getSrcIp(), f.getSrcDeviceId());
            }
        }
        portsByPair.forEach((pair, ports) -> {
            if (ports.size() >= portThreshold) {
                String src = pair.substring(0, pair.indexOf('>'));
                String dst = pair.substring(pair.indexOf('>') + 1);
                alerts.raise(rule, null, "ports:" + pair, deviceBySource.get(src), src,
                        "Varredura de portas: " + src + " tentou " + ports.size() + " portas de " + dst,
                        "Portas: " + summarize(ports));
            }
        });
        localHostsBySource.forEach((src, hosts) -> {
            if (hosts.size() >= hostThreshold) {
                alerts.raise(rule, null, "hosts:" + src, deviceBySource.get(src), src,
                        "Varredura da rede: " + src + " contatou " + hosts.size() + " equipamentos",
                        "Em " + rule.intValue("windowMinutes") + " minutos.");
            }
        });
    }

    private void suspiciousPort(TrafficFlow f) {
        RuleSettings rule = rules.settings(RuleType.SUSPICIOUS_PORT);
        if (!rule.enabled() || f.getDstPort() == null || !rule.ports("ports").contains(f.getDstPort())) {
            return;
        }
        UUID device = f.getSrcDeviceId() != null ? f.getSrcDeviceId() : f.getDstDeviceId();
        String program = f.getProcessName() != null ? " pelo programa " + f.getProcessName() : "";
        alerts.raise(rule, null, f.getSrcIp() + ">" + f.getDstIp() + ":" + f.getDstPort(), device, f.getSrcIp(),
                "Conexão em porta suspeita: " + f.getSrcIp() + " → " + f.getDstIp() + ":" + f.getDstPort(),
                f.getProtocol() + program + ".");
    }

    private void externalAdminAccess(TrafficFlow f, List<Ipv4Network> networks) {
        RuleSettings rule = rules.settings(RuleType.EXTERNAL_ADMIN_ACCESS);
        if (!rule.enabled() || f.getDstPort() == null || !rule.ports("ports").contains(f.getDstPort())
                || inScope(networks, f.getSrcIp()) || !inScope(networks, f.getDstIp())) {
            return;
        }
        alerts.raise(rule, null, f.getSrcIp() + ">" + f.getDstIp() + ":" + f.getDstPort(), f.getDstDeviceId(),
                f.getDstIp(), "Acesso de fora da rede a " + describePort(f.getDstPort()) + " em " + f.getDstIp(),
                "Origem: " + f.getSrcIp() + (f.getProcessName() != null ? " · programa " + f.getProcessName() : ""));
    }

    // ------------------------------------------------------------------ apoio

    private Optional<Device> device(UUID id) {
        return id == null ? Optional.empty() : devices.findById(id);
    }

    private static boolean inScope(List<Ipv4Network> networks, String ip) {
        return networks.stream().anyMatch(n -> n.contains(ip));
    }

    private static String describePort(int port) {
        String name = TrafficServices.nameOf(TrafficProtocol.TCP, port);
        return name != null ? port + " (" + name + ")" : String.valueOf(port);
    }

    private static String label(Device d) {
        String name = d.getCustomName() != null ? d.getCustomName() : d.getHostname();
        return name != null && !name.isBlank() ? name + " (" + d.getIpAddress() + ")" : d.getIpAddress();
    }

    private static String evidence(ActivityEvent e) {
        return e.details() != null ? e.title() + ". " + e.details() : e.title();
    }

    private static String summarize(Set<Integer> ports) {
        List<Integer> sorted = ports.stream().sorted().toList();
        String shown = sorted.stream().limit(15).map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
        return sorted.size() > 15 ? shown + "… (" + sorted.size() + " no total)" : shown;
    }
}
