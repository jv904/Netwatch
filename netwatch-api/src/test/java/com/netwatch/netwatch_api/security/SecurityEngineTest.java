package com.netwatch.netwatch_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.netwatch.netwatch_api.activity.ActivityEvent;
import com.netwatch.netwatch_api.activity.EventType;
import com.netwatch.netwatch_api.activity.NetworkEventRepository;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;

/** Regras de eventos e configuração do motor. Fora do horário fica desligada para não depender do relógio. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityEngineTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SecurityRuleService rules;

    @Autowired
    SecurityAlertRepository alerts;

    @Autowired
    NetworkEventRepository events;

    @Autowired
    DeviceService deviceService;

    @Autowired
    DeviceRepository devices;

    @Autowired
    ApplicationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        clean();
        for (RuleType type : RuleType.values()) {
            rules.update(type, type != RuleType.OFF_HOURS, type.defaultSeverity(), Map.of());
        }
    }

    @AfterEach
    void clean() {
        alerts.deleteAll();
        events.deleteAll();
        devices.deleteAll();
    }

    List<SecurityAlert> alertsOf(RuleType rule) {
        return alerts.findAll().stream().filter(a -> a.getRule() == rule).toList();
    }

    @Test
    void unknownDeviceRaisesOneAlertThatCountsRepeats() {
        Device device = deviceService.registerObservation(
                new DeviceObservation("192.168.0.40", "aa:00:00:00:00:40", null, "Espressif")).device();

        List<SecurityAlert> raised = alertsOf(RuleType.UNKNOWN_DEVICE);
        assertThat(raised).hasSize(1);
        SecurityAlert alert = raised.getFirst();
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(alert.getDeviceId()).isEqualTo(device.getId());
        assertThat(alert.getTitle()).isEqualTo("Dispositivo desconhecido na rede: 192.168.0.40 (Espressif)");

        publisher.publishEvent(ActivityEvent.of(EventType.DEVICE_DISCOVERED, device.getId(), "192.168.0.40", "de novo"));
        assertThat(alertsOf(RuleType.UNKNOWN_DEVICE)).singleElement()
                .satisfies(a -> assertThat(a.getOccurrences()).isEqualTo(2));
    }

    @Test
    void gatewayIpClaimedByAnotherMacIsCritical() {
        deviceService.registerObservation(new DeviceObservation("192.168.0.1", "aa:00:00:00:00:01", "roteador", null));
        deviceService.registerObservation(new DeviceObservation("192.168.0.1", "bb:00:00:00:00:99", null, null));

        assertThat(alertsOf(RuleType.IP_CONFLICT)).singleElement().satisfies(a -> {
            assertThat(a.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
            assertThat(a.getTitle()).isEqualTo("Conflito de IP em 192.168.0.1 (equipamento crítico)");
            assertThat(a.getEvidence()).contains("bb:00:00:00:00:99").contains("aa:00:00:00:00:01");
        });
    }

    @Test
    void newRiskyPortOnKnownDevice() {
        Device server = deviceService.registerObservation(
                new DeviceObservation("192.168.0.50", "aa:00:00:00:00:50", "servidor", null)).device();
        deviceService.setRecognized(server.getId(), true);
        alerts.deleteAll();

        publisher.publishEvent(ActivityEvent.of(EventType.PORT_OPENED, server.getId(), "192.168.0.50",
                "Porta 3389 passou a aceitar conexões").withPort(3389));

        assertThat(alertsOf(RuleType.NEW_OPEN_PORT)).singleElement()
                .satisfies(a -> assertThat(a.getTitle()).isEqualTo(
                        "Porta nova 3389 (RDP (Área de Trabalho Remota)) em servidor (192.168.0.50)"));
        assertThat(alertsOf(RuleType.RISKY_SERVICE)).singleElement()
                .satisfies(a -> assertThat(a.getSeverity()).isEqualTo(AlertSeverity.HIGH));
    }

    @Test
    void disabledRuleDoesNotAlert() throws Exception {
        mvc.perform(put("/api/security/rules/UNKNOWN_DEVICE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"severity\":\"LOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.severity").value("LOW"));

        deviceService.registerObservation(new DeviceObservation("192.168.0.60", "aa:00:00:00:00:60", null, null));
        assertThat(alertsOf(RuleType.UNKNOWN_DEVICE)).isEmpty();
    }

    @Test
    void rulesApiListsAndValidatesParameters() throws Exception {
        mvc.perform(get("/api/security/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(RuleType.values().length))
                .andExpect(jsonPath("$[?(@.code == 'RISKY_SERVICE')].params[0].value")
                        .value(org.hamcrest.Matchers.hasItem("21,23,139,445,1433,1883,3306,3389,5432,5900,27017,37777")));

        mvc.perform(put("/api/security/rules/RISKY_SERVICE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"severity\":\"CRITICAL\",\"params\":{\"ports\":\"3389, 22\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params[0].value").value("22,3389"))
                .andExpect(jsonPath("$.params[0].defaultValue").value("21,23,139,445,1433,1883,3306,3389,5432,5900,27017,37777"));

        mvc.perform(put("/api/security/rules/RISKY_SERVICE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"severity\":\"HIGH\",\"params\":{\"ports\":\"abc\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/security/rules/OFF_HOURS").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"severity\":\"HIGH\",\"params\":{\"inexistente\":\"1\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryCountsActiveAlerts() throws Exception {
        deviceService.registerObservation(new DeviceObservation("192.168.0.70", "aa:00:00:00:00:70", null, null));

        mvc.perform(get("/api/security/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAlerts").value(1))
                .andExpect(jsonPath("$.medium").value(1))
                .andExpect(jsonPath("$.rulesEnabled").value(RuleType.values().length - 1))
                .andExpect(jsonPath("$.recent[0].ruleTitle").value("Dispositivo desconhecido na rede"))
                .andExpect(jsonPath("$.recent[0].recommendation").isNotEmpty());

        mvc.perform(get("/api/security/alerts").param("status", "OPEN").param("rule", "UNKNOWN_DEVICE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].deviceName").value("Dispositivo desconhecido"));
    }
}
