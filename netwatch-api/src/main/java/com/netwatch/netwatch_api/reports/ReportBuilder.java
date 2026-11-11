package com.netwatch.netwatch_api.reports;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.activity.NetworkEvent;
import com.netwatch.netwatch_api.activity.NetworkEventRepository;
import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceStatus;
import com.netwatch.netwatch_api.device.DeviceType;
import com.netwatch.netwatch_api.device.dto.DeviceResponse;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks.AuthorizedNetwork;
import com.netwatch.netwatch_api.discovery.DiscoveryRun;
import com.netwatch.netwatch_api.discovery.DiscoveryRunRepository;
import com.netwatch.netwatch_api.discovery.DiscoveryRunStatus;
import com.netwatch.netwatch_api.discovery.Ipv4Network;
import com.netwatch.netwatch_api.ports.DevicePort;
import com.netwatch.netwatch_api.ports.DevicePortRepository;
import com.netwatch.netwatch_api.reports.ReportDocument.Chart;
import com.netwatch.netwatch_api.reports.ReportDocument.Kpi;
import com.netwatch.netwatch_api.reports.ReportDocument.Point;
import com.netwatch.netwatch_api.reports.ReportDocument.Section;
import com.netwatch.netwatch_api.reports.ReportDocument.Table;
import com.netwatch.netwatch_api.security.AlertNote;
import com.netwatch.netwatch_api.security.AlertNoteRepository;
import com.netwatch.netwatch_api.security.AlertSeverity;
import com.netwatch.netwatch_api.security.AlertStatus;
import com.netwatch.netwatch_api.security.RuleType;
import com.netwatch.netwatch_api.security.SecurityAlert;
import com.netwatch.netwatch_api.security.SecurityAlertRepository;
import com.netwatch.netwatch_api.security.SecurityRuleService;
import com.netwatch.netwatch_api.traffic.CaptureMode;
import com.netwatch.netwatch_api.traffic.CaptureSession;
import com.netwatch.netwatch_api.traffic.CaptureSessionRepository;
import com.netwatch.netwatch_api.traffic.TrafficFlow;
import com.netwatch.netwatch_api.traffic.TrafficFlowRepository;
import com.netwatch.netwatch_api.traffic.TrafficMinuteStat;
import com.netwatch.netwatch_api.traffic.TrafficMinuteStatRepository;
import com.netwatch.netwatch_api.traffic.TrafficServices;

/** Monta o conteúdo de cada tipo de relatório a partir dos dados reais do período. */
@Component
@Transactional(readOnly = true)
class ReportBuilder {

    /** Linhas máximas de uma tabela (o restante é informado como "mais N"). */
    static final int MAX_ROWS = 500;
    private static final int TOP = 10;

    private final DeviceRepository devices;
    private final DevicePortRepository devicePorts;
    private final DiscoveryRunRepository discoveryRuns;
    private final NetworkEventRepository events;
    private final SecurityAlertRepository alerts;
    private final AlertNoteRepository alertNotes;
    private final SecurityRuleService rules;
    private final TrafficFlowRepository flows;
    private final TrafficMinuteStatRepository trafficStats;
    private final CaptureSessionRepository captureSessions;
    private final AuthorizedNetworks authorizedNetworks;
    private final Clock clock;

    ReportBuilder(DeviceRepository devices, DevicePortRepository devicePorts, DiscoveryRunRepository discoveryRuns,
            NetworkEventRepository events, SecurityAlertRepository alerts, AlertNoteRepository alertNotes,
            SecurityRuleService rules, TrafficFlowRepository flows, TrafficMinuteStatRepository trafficStats,
            CaptureSessionRepository captureSessions, AuthorizedNetworks authorizedNetworks, Clock clock) {
        this.devices = devices;
        this.devicePorts = devicePorts;
        this.discoveryRuns = discoveryRuns;
        this.events = events;
        this.alerts = alerts;
        this.alertNotes = alertNotes;
        this.rules = rules;
        this.flows = flows;
        this.trafficStats = trafficStats;
        this.captureSessions = captureSessions;
        this.authorizedNetworks = authorizedNetworks;
        this.clock = clock;
    }

    ReportDocument build(ReportType type, ReportPeriod period) {
        ZoneId zone = ZoneId.systemDefault();
        Context ctx = new Context(period, new Fmt(zone), devices.findAll().stream()
                .collect(Collectors.toMap(Device::getId, Function.identity())));
        List<Section> sections = switch (type) {
            case OVERVIEW -> overview(ctx);
            case INVENTORY -> inventory(ctx);
            case EXPOSURE -> exposure(ctx);
            case SECURITY -> security(ctx);
            case TRAFFIC -> traffic(ctx);
        };
        return new ReportDocument(type.title(), type.description(), period.from(), period.to(), period.label(),
                clock.instant(), zone.getId(), sections);
    }

    /** Dados comuns durante a montagem de um relatório. */
    private record Context(ReportPeriod period, Fmt fmt, Map<UUID, Device> devices) {

        String deviceName(UUID id, String fallbackIp) {
            Device d = id != null ? devices.get(id) : null;
            return d != null ? DeviceResponse.displayName(d) + " (" + d.getIpAddress() + ")" : Fmt.orDash(fallbackIp);
        }
    }

    // ================================================================== relatório geral

    private List<Section> overview(Context ctx) {
        ReportPeriod p = ctx.period();
        List<NetworkEvent> periodEvents = periodEvents(p);
        long newDevices = count(periodEvents, EventType.DEVICE_DISCOVERED);
        long total = ctx.devices().size();
        long active = ctx.devices().values().stream().filter(d -> d.getStatus() == DeviceStatus.ACTIVE).count();
        long unknown = ctx.devices().values().stream().filter(d -> !d.isRecognized()).count();
        long openPorts = devicePorts.countByState(PortState.OPEN);
        List<SecurityAlert> periodAlerts = periodAlerts(p);
        long activeAlerts = alerts.findByStatusIn(List.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED)).size();
        long critical = periodAlerts.stream().filter(a -> a.getSeverity() == AlertSeverity.CRITICAL).count();

        String summary = "No período, " + plural(newDevices, "nenhum dispositivo novo apareceu",
                "1 dispositivo novo apareceu", " dispositivos novos apareceram")
                + " e " + plural(periodAlerts.size(), "nenhum alerta foi aberto", "1 alerta foi aberto",
                        " alertas foram abertos")
                + (critical > 0 ? " (" + plural(critical, "", "1 crítico", " críticos") + ")" : "")
                + ". Hoje há " + plural(activeAlerts, "nenhum alerta ativo", "1 alerta ativo", " alertas ativos")
                + " e " + plural(unknown, "todos os dispositivos estão reconhecidos",
                        "1 dispositivo ainda não reconhecido", " dispositivos ainda não reconhecidos") + ".";

        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Resumo", summary, List.of(
                Kpi.of("Dispositivos", Fmt.num(total), Fmt.num(active) + " ativos", "neutral"),
                Kpi.of("Novos no período", Fmt.num(newDevices), null, newDevices > 0 ? "warning" : "good"),
                Kpi.of("Não reconhecidos", Fmt.num(unknown), Fmt.percent(unknown, total) + " do inventário",
                        unknown > 0 ? "warning" : "good"),
                Kpi.of("Portas abertas", Fmt.num(openPorts), "em todos os dispositivos verificados", "neutral"),
                Kpi.of("Alertas no período", Fmt.num(periodAlerts.size()), critical + " críticos",
                        critical > 0 ? "critical" : periodAlerts.isEmpty() ? "good" : "warning"),
                Kpi.of("Alertas ativos agora", Fmt.num(activeAlerts), null, activeAlerts > 0 ? "warning" : "good"),
                Kpi.of("Eventos no período", Fmt.num(periodEvents.size()), null, "neutral")), null, List.of()));

        sections.add(new Section("Atividade ao longo do período", null, List.of(),
                eventsPerDay(ctx, periodEvents), List.of(eventsByType(periodEvents))));

        List<List<String>> newRows = periodEvents.stream()
                .filter(e -> e.getType() == EventType.DEVICE_DISCOVERED)
                .map(e -> List.of(ctx.fmt().dateTime(e.getOccurredAt()), Fmt.orDash(e.getIpAddress()),
                        Fmt.orDash(e.getDetails())))
                .toList();
        sections.add(new Section("Dispositivos novos", newRows.isEmpty() ? "Nenhum equipamento novo no período." : null,
                List.of(), null, List.of(table("Vistos pela primeira vez", List.of("Quando", "IP", "MAC / fabricante"), newRows))));

        sections.add(discoverySection(ctx));
        sections.add(new Section("Exposição", null, List.of(), exposedServicesChart(), List.of()));
        sections.add(new Section("Alertas abertos no período", periodAlerts.isEmpty() ? "Nenhum alerta no período." : null,
                severityKpis(periodAlerts), null, List.of(alertsTable(ctx, "Alertas", periodAlerts))));
        sections.add(trafficSummarySection(ctx));
        return sections;
    }

    private Section discoverySection(Context ctx) {
        List<DiscoveryRun> runs = discoveryRuns.findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAt(
                ctx.period().from(), ctx.period().to());
        long failed = runs.stream().filter(r -> r.getStatus() == DiscoveryRunStatus.FAILED).count();
        List<List<String>> rows = runs.stream().map(r -> List.of(
                ctx.fmt().dateTime(r.getStartedAt()), r.getCidr(), discoveryStatus(r.getStatus()),
                Fmt.num(r.getDevicesFound()), Fmt.num(r.getNewDevices()), Fmt.num(r.getInactivated()))).toList();
        return new Section("Descobertas", runs.isEmpty() ? "Nenhuma descoberta foi executada no período." : null,
                List.of(Kpi.of("Execuções", Fmt.num(runs.size())),
                        Kpi.of("Com falha", Fmt.num(failed), null, failed > 0 ? "warning" : "good")),
                null,
                List.of(table("Execuções", List.of("Início", "Faixa", "Situação", "Encontrados", "Novos", "Inativados"),
                        rows)));
    }

    // ================================================================== inventário

    private List<Section> inventory(Context ctx) {
        List<Device> list = ctx.devices().values().stream()
                .sorted(Comparator.comparingLong((Device d) -> Ipv4Network.toInt(d.getIpAddress()).orElse(0) & 0xffffffffL))
                .toList();
        long active = list.stream().filter(d -> d.getStatus() == DeviceStatus.ACTIVE).count();
        long unknown = list.stream().filter(d -> !d.isRecognized()).count();
        long noVendor = list.stream().filter(d -> d.getVendor() == null).count();
        long seenInPeriod = list.stream().filter(d -> ctx.period().contains(d.getLastSeenAt())).count();

        Map<String, Long> byVendor = list.stream().collect(Collectors.groupingBy(
                d -> d.getVendor() != null ? d.getVendor() : "Não identificado", Collectors.counting()));
        Chart vendorChart = new Chart("Principais fabricantes", "BAR", "dispositivos", top(byVendor, TOP));
        Map<String, Long> byType = list.stream().collect(Collectors.groupingBy(d -> typeLabel(d.getType()),
                Collectors.counting()));

        List<List<String>> rows = list.stream().map(d -> List.of(
                DeviceResponse.displayName(d), d.getIpAddress(), Fmt.orDash(d.getMacAddress()), Fmt.orDash(d.getVendor()),
                typeLabel(d.getType()), d.getStatus() == DeviceStatus.ACTIVE ? "Ativo" : "Inativo",
                d.isRecognized() ? "Sim" : "Não", ctx.fmt().dateTime(d.getFirstSeenAt()),
                ctx.fmt().dateTime(d.getLastSeenAt()))).toList();

        return List.of(
                new Section("Resumo", Fmt.num(list.size()) + " dispositivos no inventário; " + Fmt.num(seenInPeriod)
                        + " foram vistos no período.", List.of(
                        Kpi.of("Dispositivos", Fmt.num(list.size())),
                        Kpi.of("Ativos", Fmt.num(active), Fmt.percent(active, list.size()), "good"),
                        Kpi.of("Inativos", Fmt.num(list.size() - active)),
                        Kpi.of("Não reconhecidos", Fmt.num(unknown), null, unknown > 0 ? "warning" : "good"),
                        Kpi.of("Sem fabricante identificado", Fmt.num(noVendor)),
                        Kpi.of("Vistos no período", Fmt.num(seenInPeriod))),
                        vendorChart,
                        List.of(table("Por tipo", List.of("Tipo", "Dispositivos"), byType.entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .map(e -> List.of(e.getKey(), Fmt.num(e.getValue()))).toList()))),
                new Section("Dispositivos", null, List.of(), null, List.of(table("Inventário",
                        List.of("Nome", "IP", "MAC", "Fabricante", "Tipo", "Situação", "Reconhecido", "Primeira vez",
                                "Última vez"), rows))));
    }

    // ================================================================== exposição

    private List<Section> exposure(Context ctx) {
        List<DevicePort> open = devicePorts.findByStateOrderByDeviceIdAscPortAsc(PortState.OPEN);
        Set<Integer> risky = rules.settings(RuleType.RISKY_SERVICE).ports("ports");
        long riskyOpen = open.stream().filter(p -> risky.contains(p.getPort())).count();
        long devicesWithOpen = open.stream().map(DevicePort::getDeviceId).distinct().count();
        long services = open.stream().map(DevicePort::getPort).distinct().count();

        List<DevicePort> sorted = open.stream()
                .sorted(Comparator.comparing((DevicePort p) -> !risky.contains(p.getPort()))
                        .thenComparing(p -> ctx.deviceName(p.getDeviceId(), null))
                        .thenComparingInt(DevicePort::getPort))
                .toList();
        List<List<String>> rows = sorted.stream().map(p -> {
            Device d = ctx.devices().get(p.getDeviceId());
            return List.of(d != null ? DeviceResponse.displayName(d) : "—", d != null ? d.getIpAddress() : "—",
                    String.valueOf(p.getPort()), Fmt.orDash(p.getService()), risky.contains(p.getPort()) ? "Sim" : "Não",
                    ctx.fmt().dateTime(p.getOpenSince()), ctx.fmt().dateTime(p.getLastCheckedAt()));
        }).toList();

        List<NetworkEvent> changes = periodEvents(ctx.period()).stream()
                .filter(e -> e.getType() == EventType.PORT_OPENED || e.getType() == EventType.PORT_CLOSED)
                .toList();
        List<List<String>> changeRows = changes.stream().map(e -> List.of(ctx.fmt().dateTime(e.getOccurredAt()),
                e.getType() == EventType.PORT_OPENED ? "Passou a aceitar conexões" : "Deixou de aceitar conexões",
                e.getTitle())).toList();

        String summary = riskyOpen > 0
                ? Fmt.num(riskyOpen) + " portas de serviços de risco estão abertas. Avalie se cada serviço é necessário."
                : "Nenhuma porta de serviço de risco aberta nos dispositivos verificados.";
        return List.of(
                new Section("Resumo", summary, List.of(
                        Kpi.of("Portas abertas", Fmt.num(open.size())),
                        Kpi.of("Dispositivos expostos", Fmt.num(devicesWithOpen)),
                        Kpi.of("Serviços distintos", Fmt.num(services)),
                        Kpi.of("Portas de risco abertas", Fmt.num(riskyOpen), null, riskyOpen > 0 ? "critical" : "good")),
                        exposedServicesChart(), List.of()),
                new Section("Portas abertas", "Serviços de risco aparecem primeiro.", List.of(), null, List.of(
                        table("Portas que aceitam conexões",
                                List.of("Dispositivo", "IP", "Porta", "Serviço", "Risco", "Aberta desde", "Última verificação"),
                                rows))),
                new Section("Mudanças no período", changes.isEmpty() ? "Nenhuma porta mudou de situação no período." : null,
                        List.of(), null, List.of(table("Mudanças", List.of("Quando", "Mudança", "Detalhe"), changeRows))));
    }

    private Chart exposedServicesChart() {
        List<Point> points = devicePorts.findExposedServices().stream().limit(TOP)
                .map(s -> new Point(s.getPort() + (s.getService() != null ? " · " + s.getService() : ""), s.getDevices()))
                .toList();
        return new Chart("Serviços mais expostos", "BAR", "dispositivos", points);
    }

    // ================================================================== segurança

    private List<Section> security(Context ctx) {
        ReportPeriod p = ctx.period();
        List<SecurityAlert> opened = periodAlerts(p);
        List<AlertNote> changes = alertNotes.findByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                AlertNote.Kind.STATUS, p.from(), p.to());
        Map<UUID, SecurityAlert> byId = new HashMap<>();
        Set<UUID> ids = changes.stream().map(AlertNote::getAlertId).collect(Collectors.toSet());
        alerts.findAllById(ids).forEach(a -> byId.put(a.getId(), a));

        long resolved = changes.stream().filter(n -> n.getToStatus() == AlertStatus.RESOLVED).count();
        long dismissed = changes.stream().filter(n -> n.getToStatus() == AlertStatus.DISMISSED).count();
        List<Duration> times = changes.stream()
                .filter(n -> n.getToStatus() == AlertStatus.RESOLVED && byId.containsKey(n.getAlertId()))
                .map(n -> Duration.between(byId.get(n.getAlertId()).getFirstSeenAt(), n.getCreatedAt()))
                .toList();
        Duration meanToResolve = times.isEmpty() ? null
                : Duration.ofSeconds((long) times.stream().mapToLong(Duration::toSeconds).average().orElse(0));

        List<SecurityAlert> active = alerts.findByStatusIn(List.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED)).stream()
                .sorted(Comparator.comparing(SecurityAlert::getSeverity).reversed()
                        .thenComparing(SecurityAlert::getLastSeenAt, Comparator.reverseOrder()))
                .toList();

        Map<String, Long> byRule = opened.stream().collect(Collectors.groupingBy(a -> a.getRule().title(),
                Collectors.counting()));
        List<Kpi> kpis = new ArrayList<>(severityKpis(opened));
        kpis.add(Kpi.of("Resolvidos no período", Fmt.num(resolved), null, "good"));
        kpis.add(Kpi.of("Descartados no período", Fmt.num(dismissed)));
        kpis.add(Kpi.of("Tempo médio até resolver", Fmt.duration(meanToResolve)));
        kpis.add(Kpi.of("Ativos agora", Fmt.num(active.size()), null, active.isEmpty() ? "good" : "warning"));

        String summary = opened.isEmpty() ? "Nenhum alerta foi aberto no período."
                : Fmt.num(opened.size()) + " alertas abertos no período; " + Fmt.num(resolved) + " resolvidos e "
                        + Fmt.num(dismissed) + " descartados.";
        return List.of(
                new Section("Resumo", summary, kpis, new Chart("Alertas por regra", "BAR", "alertas", top(byRule, TOP)),
                        List.of()),
                new Section("Alertas abertos no período", null, List.of(), null,
                        List.of(alertsTable(ctx, "Alertas do período", opened))),
                new Section("Alertas ativos agora", active.isEmpty() ? "Nenhum alerta ativo." : null, List.of(), null,
                        List.of(alertsTable(ctx, "Alertas ativos", active))));
    }

    private List<Kpi> severityKpis(List<SecurityAlert> list) {
        Map<AlertSeverity, Long> bySeverity = new EnumMap<>(AlertSeverity.class);
        list.forEach(a -> bySeverity.merge(a.getSeverity(), 1L, Long::sum));
        long critical = bySeverity.getOrDefault(AlertSeverity.CRITICAL, 0L);
        long high = bySeverity.getOrDefault(AlertSeverity.HIGH, 0L);
        return List.of(
                Kpi.of("Alertas abertos", Fmt.num(list.size())),
                Kpi.of("Críticos", Fmt.num(critical), null, critical > 0 ? "critical" : "good"),
                Kpi.of("Altos", Fmt.num(high), null, high > 0 ? "warning" : "good"),
                Kpi.of("Médios e baixos", Fmt.num(bySeverity.getOrDefault(AlertSeverity.MEDIUM, 0L)
                        + bySeverity.getOrDefault(AlertSeverity.LOW, 0L))));
    }

    private Table alertsTable(Context ctx, String title, List<SecurityAlert> list) {
        return table(title, List.of("Aberto em", "Gravidade", "Alerta", "Regra", "Dispositivo", "Ocorrências", "Situação"),
                list.stream().map(a -> List.of(ctx.fmt().dateTime(a.getFirstSeenAt()), severityLabel(a.getSeverity()),
                        a.getTitle(), a.getRule().title(), ctx.deviceName(a.getDeviceId(), a.getIpAddress()),
                        Fmt.num(a.getOccurrences()), statusLabel(a.getStatus()))).toList());
    }

    // ================================================================== tráfego

    private List<Section> traffic(Context ctx) {
        ReportPeriod p = ctx.period();
        List<TrafficFlow> list = flows.findByLastSeenGreaterThanEqualAndFirstSeenLessThan(p.from(), p.to());
        List<TrafficMinuteStat> stats = trafficStats
                .findByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStart(p.from(), p.to());
        List<CaptureSession> sessions = captureSessions
                .findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAt(p.from(), p.to());
        List<Ipv4Network> networks = authorizedNetworks.list().stream().map(AuthorizedNetwork::network).toList();

        long bytes = stats.stream().mapToLong(TrafficMinuteStat::getBytes).sum();
        long packets = stats.stream().mapToLong(TrafficMinuteStat::getPackets).sum();
        boolean hasVolume = bytes > 0;
        Map<Instant, Long> connectionsPerMinute = new HashMap<>();
        stats.forEach(s -> connectionsPerMinute.merge(s.getBucketStart(), s.getConnections(), Long::sum));
        long peakConnections = connectionsPerMinute.values().stream().mapToLong(Long::longValue).max().orElse(0);

        Map<String, long[]> hosts = new HashMap<>();
        Map<String, long[]> services = new HashMap<>();
        for (TrafficFlow f : list) {
            long[] src = hosts.computeIfAbsent(f.getSrcIp(), k -> new long[3]);
            src[0] += f.getBytes();
            src[2]++;
            long[] dst = hosts.computeIfAbsent(f.getDstIp(), k -> new long[3]);
            dst[1] += f.getBytes();
            dst[2]++;
            if (f.getServicePort() != null) {
                String key = f.getProtocol() + ":" + f.getServicePort();
                long[] s = services.computeIfAbsent(key, k -> new long[2]);
                s[0] += f.getBytes();
                s[1]++;
            }
        }
        long localHosts = hosts.keySet().stream().filter(ip -> networks.stream().anyMatch(n -> n.contains(ip))).count();

        // Série por dia: volume (com captura de pacotes) ou pico de conexões (tabela de conexões).
        Map<Instant, Long> perDay = new TreeMap<>();
        for (Instant day = startOfDay(p.from()); day.isBefore(p.to()); day = day.plus(1, ChronoUnit.DAYS)) {
            perDay.put(day, 0L);
        }
        if (hasVolume) {
            stats.forEach(s -> perDay.merge(startOfDay(s.getBucketStart()), s.getBytes(), Long::sum));
        } else {
            connectionsPerMinute.forEach((minute, n) -> perDay.merge(startOfDay(minute), n, Math::max));
        }
        Chart chart = new Chart(hasVolume ? "Volume por dia" : "Pico de conexões por dia", "LINE",
                hasVolume ? "bytes" : "conexões",
                perDay.entrySet().stream().map(e -> new Point(ctx.fmt().day(e.getKey()), e.getValue())).toList());

        List<List<String>> hostRows = hosts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0] + e.getValue()[1])
                        .thenComparingLong(e -> e.getValue()[2]).reversed())
                .limit(20)
                .map(e -> {
                    Device d = deviceByIp(ctx, e.getKey());
                    return List.of(e.getKey(), d != null ? DeviceResponse.displayName(d) : "—",
                            networks.stream().anyMatch(n -> n.contains(e.getKey())) ? "Sim" : "Não",
                            Fmt.num(e.getValue()[2]),
                            hasVolume ? Fmt.bytes(e.getValue()[0]) : "—", hasVolume ? Fmt.bytes(e.getValue()[1]) : "—");
                }).toList();
        List<List<String>> serviceRows = services.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0])
                        .thenComparingLong(e -> e.getValue()[1]).reversed())
                .limit(20)
                .map(e -> {
                    String[] parts = e.getKey().split(":");
                    com.netwatch.netwatch_api.traffic.TrafficProtocol protocol =
                            com.netwatch.netwatch_api.traffic.TrafficProtocol.valueOf(parts[0]);
                    int port = Integer.parseInt(parts[1]);
                    return List.of(port + "/" + protocol.name().toLowerCase(),
                            Fmt.orDash(TrafficServices.nameOf(protocol, port)), Fmt.num(e.getValue()[1]),
                            hasVolume ? Fmt.bytes(e.getValue()[0]) : "—");
                }).toList();
        List<List<String>> sessionRows = sessions.stream().map(s -> List.of(
                ctx.fmt().dateTime(s.getStartedAt()),
                s.getMode() == CaptureMode.CONNECTIONS ? "Tabela de conexões" : "Captura de pacotes",
                Fmt.orDash(s.getInterfaceDescription()), sessionStatus(s),
                s.getFinishedAt() != null ? Fmt.duration(Duration.between(s.getStartedAt(), s.getFinishedAt())) : "—",
                s.getMode() == CaptureMode.PACKETS ? Fmt.num(s.getPackets()) : "—",
                s.getMode() == CaptureMode.PACKETS ? Fmt.bytes(s.getBytes()) : "—")).toList();

        String summary = list.isEmpty()
                ? "Nenhum tráfego registrado no período. Inicie uma observação na tela Tráfego."
                : hasVolume ? null
                : "Período observado pela tabela de conexões do sistema: volume e pacotes não são medidos sem o Npcap.";
        List<Kpi> kpis = new ArrayList<>();
        kpis.add(Kpi.of("Fluxos", Fmt.num(list.size())));
        kpis.add(Kpi.of("Hosts da rede", Fmt.num(localHosts)));
        kpis.add(hasVolume ? Kpi.of("Volume", Fmt.bytes(bytes), Fmt.num(packets) + " pacotes", "neutral")
                : Kpi.of("Pico de conexões simultâneas", Fmt.num(peakConnections)));
        kpis.add(Kpi.of("Sessões de observação", Fmt.num(sessions.size())));

        return List.of(
                new Section("Resumo", summary, kpis, chart, List.of()),
                new Section("Hosts mais ativos", null, List.of(), null, List.of(table("Hosts",
                        List.of("IP", "Dispositivo", "Rede local", "Fluxos", "Enviado", "Recebido"), hostRows))),
                new Section("Serviços mais usados", null, List.of(), null, List.of(table("Serviços",
                        List.of("Porta", "Serviço", "Fluxos", "Volume"), serviceRows))),
                new Section("Sessões de observação", sessions.isEmpty() ? "Nenhuma sessão no período." : null, List.of(),
                        null, List.of(table("Sessões",
                                List.of("Início", "Fonte", "Interface", "Situação", "Duração", "Pacotes", "Volume"),
                                sessionRows))));
    }

    /** Resumo de tráfego para o relatório geral. */
    private Section trafficSummarySection(Context ctx) {
        List<Section> full = traffic(ctx);
        Section summary = full.getFirst();
        return new Section("Tráfego", summary.summary(), summary.kpis(), summary.chart(), List.of());
    }

    // ================================================================== apoio

    private List<NetworkEvent> periodEvents(ReportPeriod p) {
        return events.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAt(p.from(), p.to());
    }

    private List<SecurityAlert> periodAlerts(ReportPeriod p) {
        return alerts.findByFirstSeenAtGreaterThanEqualAndFirstSeenAtLessThanOrderByFirstSeenAtDesc(p.from(), p.to());
    }

    /** "nenhum X", "1 X" ou "N Xs" (o plural recebe o número na frente). */
    private static String plural(long n, String zero, String one, String many) {
        if (n == 0) {
            return zero;
        }
        return n == 1 ? one : Fmt.num(n) + many;
    }

    private static long count(List<NetworkEvent> list, EventType type) {
        return list.stream().filter(e -> e.getType() == type).count();
    }

    private Chart eventsPerDay(Context ctx, List<NetworkEvent> list) {
        Map<Instant, Long> perDay = new TreeMap<>();
        for (Instant day = startOfDay(ctx.period().from()); day.isBefore(ctx.period().to());
                day = day.plus(1, ChronoUnit.DAYS)) {
            perDay.put(day, 0L);
        }
        list.forEach(e -> perDay.merge(startOfDay(e.getOccurredAt()), 1L, Long::sum));
        return new Chart("Eventos por dia", "LINE", "eventos",
                perDay.entrySet().stream().map(e -> new Point(ctx.fmt().day(e.getKey()), e.getValue())).toList());
    }

    private Table eventsByType(List<NetworkEvent> list) {
        Map<EventType, Long> byType = new EnumMap<>(EventType.class);
        list.forEach(e -> byType.merge(e.getType(), 1L, Long::sum));
        return table("Eventos por tipo", List.of("Tipo", "Quantidade"), byType.entrySet().stream()
                .sorted(Map.Entry.<EventType, Long>comparingByValue().reversed())
                .map(e -> List.of(eventLabel(e.getKey()), Fmt.num(e.getValue()))).toList());
    }

    private Device deviceByIp(Context ctx, String ip) {
        return ctx.devices().values().stream().filter(d -> ip.equals(d.getIpAddress()))
                .max(Comparator.comparing(d -> d.getLastSeenAt() != null ? d.getLastSeenAt() : Instant.EPOCH))
                .orElse(null);
    }

    private static Instant startOfDay(Instant at) {
        return at.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    static Table table(String title, List<String> columns, List<List<String>> rows) {
        return new Table(title, columns, rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows, rows.size());
    }

    private static List<Point> top(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new Point(e.getKey(), e.getValue()))
                .toList();
    }

    private static String discoveryStatus(DiscoveryRunStatus status) {
        return switch (status) {
            case RUNNING -> "Em andamento";
            case COMPLETED -> "Concluída";
            case FAILED -> "Falhou";
        };
    }

    private static String sessionStatus(CaptureSession s) {
        return switch (s.getStatus()) {
            case RUNNING -> "Em andamento";
            case COMPLETED -> "Concluída";
            case STOPPED -> "Parada";
            case FAILED -> "Falhou";
        };
    }

    static String severityLabel(AlertSeverity severity) {
        return switch (severity) {
            case LOW -> "Baixa";
            case MEDIUM -> "Média";
            case HIGH -> "Alta";
            case CRITICAL -> "Crítica";
        };
    }

    private static String statusLabel(AlertStatus status) {
        return switch (status) {
            case OPEN -> "Aberto";
            case ACKNOWLEDGED -> "Reconhecido";
            case RESOLVED -> "Resolvido";
            case DISMISSED -> "Descartado";
        };
    }

    private static final Map<DeviceType, String> TYPE_LABELS = new LinkedHashMap<>();

    static {
        TYPE_LABELS.put(DeviceType.UNKNOWN, "Não definido");
        TYPE_LABELS.put(DeviceType.COMPUTER, "Computador");
        TYPE_LABELS.put(DeviceType.NOTEBOOK, "Notebook");
        TYPE_LABELS.put(DeviceType.SMARTPHONE, "Celular");
        TYPE_LABELS.put(DeviceType.TABLET, "Tablet");
        TYPE_LABELS.put(DeviceType.PRINTER, "Impressora");
        TYPE_LABELS.put(DeviceType.SERVER, "Servidor");
        TYPE_LABELS.put(DeviceType.ROUTER, "Roteador");
        TYPE_LABELS.put(DeviceType.SWITCH, "Switch");
        TYPE_LABELS.put(DeviceType.ACCESS_POINT, "Ponto de acesso");
        TYPE_LABELS.put(DeviceType.IOT, "IoT / câmera");
        TYPE_LABELS.put(DeviceType.OTHER, "Outro");
    }

    private static String typeLabel(DeviceType type) {
        return TYPE_LABELS.getOrDefault(type, type.name());
    }

    private static String eventLabel(EventType type) {
        return switch (type) {
            case DEVICE_DISCOVERED -> "Novo dispositivo";
            case DEVICE_IP_CHANGED -> "IP alterado";
            case DEVICE_IP_CONFLICT -> "Conflito de IP";
            case DEVICE_RETURNED -> "Voltou a aparecer";
            case DEVICE_INACTIVE -> "Ficou inativo";
            case DEVICE_CREATED -> "Cadastro manual";
            case DEVICE_RECOGNIZED -> "Marcado como conhecido";
            case DEVICE_UNRECOGNIZED -> "Marcado como desconhecido";
            case DEVICE_DELETED -> "Removido";
            case PORT_OPENED -> "Porta passou a aceitar conexões";
            case PORT_CLOSED -> "Porta deixou de aceitar conexões";
            case PORT_SCAN_COMPLETED -> "Verificação de portas";
            case PORT_SCAN_FAILED -> "Falha na verificação de portas";
            case DISCOVERY_COMPLETED -> "Descoberta concluída";
            case DISCOVERY_FAILED -> "Falha na descoberta";
            case CAPTURE_STARTED -> "Captura iniciada";
            case CAPTURE_FINISHED -> "Captura encerrada";
            case CAPTURE_FAILED -> "Falha na captura";
            case TRAFFIC_NEW_SERVICE -> "Novo serviço no tráfego";
            case ALERT_RAISED -> "Alerta aberto";
            case ALERT_RESOLVED -> "Alerta resolvido";
            case ALERT_DISMISSED -> "Alerta descartado";
        };
    }
}
