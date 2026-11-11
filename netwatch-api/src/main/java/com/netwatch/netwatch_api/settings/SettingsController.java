package com.netwatch.netwatch_api.settings;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.activity.NetworkEventRepository;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.discovery.AuthorizedNetworks;
import com.netwatch.netwatch_api.reports.ReportRepository;
import com.netwatch.netwatch_api.security.SecurityAlertRepository;
import com.netwatch.netwatch_api.settings.SettingsService.SettingView;
import com.netwatch.netwatch_api.traffic.CaptureService;
import com.netwatch.netwatch_api.traffic.TrafficFlowRepository;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;
    private final AuthorizedNetworks authorizedNetworks;
    private final CaptureService capture;
    private final DataSource dataSource;
    private final DeviceRepository devices;
    private final NetworkEventRepository events;
    private final TrafficFlowRepository flows;
    private final SecurityAlertRepository alerts;
    private final ReportRepository reports;

    public SettingsController(SettingsService settings, AuthorizedNetworks authorizedNetworks, CaptureService capture,
            DataSource dataSource, DeviceRepository devices, NetworkEventRepository events, TrafficFlowRepository flows,
            SecurityAlertRepository alerts, ReportRepository reports) {
        this.settings = settings;
        this.authorizedNetworks = authorizedNetworks;
        this.capture = capture;
        this.dataSource = dataSource;
        this.devices = devices;
        this.events = events;
        this.flows = flows;
        this.alerts = alerts;
        this.reports = reports;
    }

    /** Todas as configurações, com o valor em uso, o de fábrica e se foi alterado pela tela. */
    @GetMapping
    public List<SettingView> all() {
        return settings.all();
    }

    /** Altera uma ou mais configurações (tudo ou nada). Ex.: {@code {"capture.mode":"connections"}}. */
    @PutMapping
    public List<SettingView> update(@RequestBody Map<String, String> changes) {
        return settings.update(changes);
    }

    /** Volta a configuração ao valor do application.yaml. */
    @DeleteMapping("/{key}")
    public List<SettingView> restore(@PathVariable String key) {
        return settings.restore(key);
    }

    public record NetworkInfo(String cidr, String source, boolean virtual) {
    }

    public record SystemInfo(String javaVersion, String os, String database, String timeZone, Instant startedAt,
            long uptimeSeconds, List<NetworkInfo> authorizedNetworks, boolean networksConfigured, String captureMode,
            String captureReason, Map<String, Long> data) {
    }

    /** Ambiente, redes em uso, fonte de tráfego e volume de dados guardados. */
    @GetMapping("/system")
    public SystemInfo system() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        CaptureService.Availability availability = capture.availability();
        List<NetworkInfo> networks = authorizedNetworks.list().stream()
                .map(n -> new NetworkInfo(n.network().toString(), n.source(), n.virtual())).toList();
        Map<String, Long> data = new java.util.LinkedHashMap<>();
        data.put("devices", devices.count());
        data.put("events", events.count());
        data.put("flows", flows.count());
        data.put("alerts", alerts.count());
        data.put("reports", reports.count());
        return new SystemInfo(
                System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                databaseName(),
                ZoneId.systemDefault().getId(),
                Instant.ofEpochMilli(runtime.getStartTime()),
                Duration.ofMillis(runtime.getUptime()).toSeconds(),
                networks,
                !settings.cidrs(SettingKey.DISCOVERY_AUTHORIZED_NETWORKS).isEmpty(),
                availability.mode() != null ? availability.mode().name() : null,
                availability.reason(),
                data);
    }

    private String databaseName() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
        } catch (SQLException e) {
            return "indisponível";
        }
    }
}
