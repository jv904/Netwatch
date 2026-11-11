package com.netwatch.netwatch_api.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.device.dto.CreateDeviceRequest;
import com.netwatch.netwatch_api.security.RuleType;
import com.netwatch.netwatch_api.security.SecurityRuleService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivityControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DeviceService deviceService;

    @Autowired
    DeviceRepository devices;

    @Autowired
    NetworkEventRepository events;

    @Autowired
    SecurityRuleService securityRules;

    /** Motor de segurança desligado: aqui só interessam os eventos de dispositivos, não os de alertas. */
    @BeforeEach
    void setUp() {
        clean();
        setSecurityRules(false);
    }

    @AfterEach
    void tearDown() {
        clean();
        setSecurityRules(true);
    }

    void clean() {
        events.deleteAll();
        devices.deleteAll();
    }

    void setSecurityRules(boolean enabled) {
        for (RuleType type : RuleType.values()) {
            securityRules.update(type, enabled, type.defaultSeverity(), java.util.Map.of());
        }
    }

    @Test
    void deviceLifecycleIsRecorded() throws Exception {
        Device nas = deviceService.registerObservation(
                new DeviceObservation("192.168.0.10", "aa:00:00:00:00:10", "nas", "Synology")).device();
        // Mesmo MAC, outro IP.
        deviceService.registerObservation(new DeviceObservation("192.168.0.11", "aa:00:00:00:00:10", null, null));
        // Não respondeu e depois voltou, visto no tráfego.
        deviceService.markInactiveNotSeenSince(Instant.now().plusSeconds(60), ip -> true);
        deviceService.registerObservation(new DeviceObservation("192.168.0.11", "aa:00:00:00:00:10", null, null,
                DeviceObservation.Source.TRAFFIC));
        deviceService.setRecognized(nas.getId(), true);

        mvc.perform(get("/api/devices/{id}/events", nas.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[*].type").value(hasItems("DEVICE_DISCOVERED", "DEVICE_IP_CHANGED",
                        "DEVICE_INACTIVE", "DEVICE_RETURNED", "DEVICE_RECOGNIZED")))
                .andExpect(jsonPath("$.content[?(@.type == 'DEVICE_DISCOVERED')].title")
                        .value(hasItem("Novo dispositivo nas (192.168.0.10) visto na descoberta")))
                .andExpect(jsonPath("$.content[?(@.type == 'DEVICE_DISCOVERED')].severity").value(hasItem("WARNING")))
                .andExpect(jsonPath("$.content[?(@.type == 'DEVICE_DISCOVERED')].details")
                        .value(hasItem("MAC aa:00:00:00:00:10 · Synology")))
                .andExpect(jsonPath("$.content[?(@.type == 'DEVICE_IP_CHANGED')].title")
                        .value(hasItem("nas (192.168.0.11) mudou de IP: 192.168.0.10 → 192.168.0.11")))
                .andExpect(jsonPath("$.content[?(@.type == 'DEVICE_RETURNED')].title")
                        .value(hasItem("nas (192.168.0.11) voltou a ser visto no tráfego")))
                .andExpect(jsonPath("$.content[0].deviceName").value("nas"));
    }

    @Test
    void deletedDeviceKeepsItsHistory() throws Exception {
        Device printer = deviceService.create(new CreateDeviceRequest("192.168.0.20", null, null, "Impressora RH",
                null, null));
        deviceService.delete(printer.getId());

        assertThat(events.findAll()).hasSize(2).allMatch(e -> e.getDeviceId() == null);
        mvc.perform(get("/api/events").param("q", "impressora rh"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].type").value(hasItems("DEVICE_CREATED", "DEVICE_DELETED")));
    }

    @Test
    void filtersAndSummary() throws Exception {
        deviceService.registerObservation(new DeviceObservation("192.168.0.30", "aa:00:00:00:00:30", null, null));
        deviceService.registerObservation(new DeviceObservation("192.168.0.31", "aa:00:00:00:00:31", null, null));
        UUID cam = devices.findByMacAddress("aa:00:00:00:00:31").orElseThrow().getId();
        deviceService.setRecognized(cam, true);

        mvc.perform(get("/api/events").param("severity", "WARNING").param("window", "24h"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/events").param("type", "DEVICE_RECOGNIZED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/events").param("category", "PORT"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/events/summary").param("window", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window").value("24h"))
                .andExpect(jsonPath("$.bucketSeconds").value(3600))
                .andExpect(jsonPath("$.totals.total").value(3))
                .andExpect(jsonPath("$.totals.warnings").value(2))
                .andExpect(jsonPath("$.totals.info").value(1))
                .andExpect(jsonPath("$.totals.newDevices").value(2))
                .andExpect(jsonPath("$.byHour.length()").value(24))
                .andExpect(jsonPath("$.byType[0].type").value("DEVICE_DISCOVERED"))
                .andExpect(jsonPath("$.byType[0].count").value(2))
                .andExpect(jsonPath("$.topDevices[0].warnings").value(1))
                .andExpect(jsonPath("$.series[-1:].total").value(hasItem(3)));
    }

    @Test
    void rejectsInvalidWindowAndUnknownDevice() throws Exception {
        mvc.perform(get("/api/events/summary").param("window", "1y")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/devices/{id}/events", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
