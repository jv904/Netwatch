package com.netwatch.netwatch_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

/** Ciclo de vida dos alertas (Fase 8). Fora do horário fica desligada para não depender do relógio. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertManagementTest {

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

    Device unknownDevice(String ip, String mac) {
        return deviceService.registerObservation(new DeviceObservation(ip, mac, null, null)).device();
    }

    UUID onlyAlert(RuleType rule) {
        List<SecurityAlert> list = alerts.findAll().stream().filter(a -> a.getRule() == rule).toList();
        assertThat(list).hasSize(1);
        return list.getFirst().getId();
    }

    void action(UUID id, String action, String body, int expectedStatus) throws Exception {
        mvc.perform(post("/api/security/alerts/{id}/" + action, id).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void fullLifecycleWithTimeline() throws Exception {
        unknownDevice("192.168.0.80", "aa:00:00:00:00:80");
        UUID id = onlyAlert(RuleType.UNKNOWN_DEVICE);

        action(id, "acknowledge", "{}", 200);
        action(id, "acknowledge", "{}", 409);
        mvc.perform(post("/api/security/alerts/{id}/comments", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Verificando com o TI\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/security/alerts/{id}/resolve", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Era o notebook de um visitante\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alert.status").value("RESOLVED"))
                .andExpect(jsonPath("$.alert.resolutionNote").value("Era o notebook de um visitante"))
                .andExpect(jsonPath("$.timeline.length()").value(3))
                .andExpect(jsonPath("$.timeline[0].toStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.timeline[1].kind").value("COMMENT"))
                .andExpect(jsonPath("$.timeline[2].fromStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.timeline[2].toStatus").value("RESOLVED"));
        action(id, "resolve", "{}", 409);
        action(id, "reopen", "{\"note\":\"Voltou a aparecer\"}", 200);
        action(id, "dismiss", "{\"note\":\"sem motivo\"}", 400);

        mvc.perform(get("/api/security/alerts/{id}", id))
                .andExpect(jsonPath("$.alert.status").value("OPEN"))
                .andExpect(jsonPath("$.alert.resolutionNote").doesNotExist());
        mvc.perform(get("/api/security/alerts/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void suppressedDismissalSwallowsRepeats() throws Exception {
        Device device = unknownDevice("192.168.0.81", "aa:00:00:00:00:81");
        UUID id = onlyAlert(RuleType.UNKNOWN_DEVICE);
        action(id, "dismiss", "{\"reason\":\"FALSE_POSITIVE\",\"suppress\":true}", 200);

        publisher.publishEvent(ActivityEvent.of(EventType.DEVICE_DISCOVERED, device.getId(), "192.168.0.81", "de novo"));

        assertThat(alerts.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AlertStatus.DISMISSED);
            assertThat(a.getOccurrences()).isEqualTo(2);
            assertThat(a.isSuppressed()).isTrue();
        });
        mvc.perform(get("/api/security/alerts/counts"))
                .andExpect(jsonPath("$.active").value(0))
                .andExpect(jsonPath("$.dismissed").value(1));
    }

    @Test
    void dismissalWithoutSuppressionOpensNewAlertOnRepeat() throws Exception {
        Device device = unknownDevice("192.168.0.82", "aa:00:00:00:00:82");
        action(onlyAlert(RuleType.UNKNOWN_DEVICE), "dismiss", "{\"reason\":\"ACCEPTED_RISK\"}", 200);

        publisher.publishEvent(ActivityEvent.of(EventType.DEVICE_DISCOVERED, device.getId(), "192.168.0.82", "de novo"));

        assertThat(alerts.findAll()).extracting(SecurityAlert::getStatus)
                .containsExactlyInAnyOrder(AlertStatus.DISMISSED, AlertStatus.OPEN);
    }

    @Test
    void recognizingDeviceResolvesUnknownDeviceAlert() {
        Device device = unknownDevice("192.168.0.83", "aa:00:00:00:00:83");
        UUID id = onlyAlert(RuleType.UNKNOWN_DEVICE);

        deviceService.setRecognized(device.getId(), true);

        SecurityAlert alert = alerts.findById(id).orElseThrow();
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getResolutionNote()).contains("marcado como conhecido");
        assertThat(devices.findById(device.getId()).orElseThrow().isRecognized()).isTrue();
    }

    @Test
    void closedPortResolvesItsAlertsOnly() {
        Device server = unknownDevice("192.168.0.84", "aa:00:00:00:00:84");
        deviceService.setRecognized(server.getId(), true);
        alerts.deleteAll();
        publisher.publishEvent(ActivityEvent.of(EventType.PORT_OPENED, server.getId(), "192.168.0.84", "3389")
                .withPort(3389));
        publisher.publishEvent(ActivityEvent.of(EventType.PORT_OPENED, server.getId(), "192.168.0.84", "23")
                .withPort(23));
        assertThat(alerts.findAll()).hasSize(4);

        publisher.publishEvent(ActivityEvent.of(EventType.PORT_CLOSED, server.getId(), "192.168.0.84", "3389")
                .withPort(3389));

        assertThat(alerts.findAll()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(
                a.getDedupKey().endsWith(":3389") ? AlertStatus.RESOLVED : AlertStatus.OPEN));
    }

    @Test
    void bulkSkipsAlertsThatCannotChange() throws Exception {
        unknownDevice("192.168.0.85", "aa:00:00:00:00:85");
        unknownDevice("192.168.0.86", "aa:00:00:00:00:86");
        List<UUID> ids = alerts.findAll().stream().map(SecurityAlert::getId).toList();
        action(ids.getFirst(), "resolve", "{}", 200);

        String body = "{\"ids\":[\"" + ids.get(0) + "\",\"" + ids.get(1) + "\",\"" + UUID.randomUUID()
                + "\"],\"action\":\"RESOLVE\",\"note\":\"Lote\"}";
        mvc.perform(post("/api/security/alerts/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(1))
                .andExpect(jsonPath("$.skipped").value(2));
        mvc.perform(post("/api/security/alerts/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + ids.get(0) + "\"],\"action\":\"DISMISS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countsStateFilterAndHistory() throws Exception {
        unknownDevice("192.168.0.87", "aa:00:00:00:00:87");
        unknownDevice("192.168.0.88", "aa:00:00:00:00:88");
        action(alerts.findAll().getFirst().getId(), "acknowledge", "{}", 200);

        mvc.perform(get("/api/security/alerts/counts"))
                .andExpect(jsonPath("$.active").value(2))
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.acknowledged").value(1))
                .andExpect(jsonPath("$.medium").value(2))
                .andExpect(jsonPath("$.lastRaisedAt").isNotEmpty());
        mvc.perform(get("/api/security/alerts").param("state", "active").param("q", "192.168.0.88"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/security/alerts").param("state", "closed"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/security/alerts").param("state", "qualquer")).andExpect(status().isBadRequest());

        mvc.perform(get("/api/events").param("category", "SECURITY"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].type").value(hasItem("ALERT_RAISED")));
    }
}
