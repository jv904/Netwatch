package com.netwatch.netwatch_api.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.netwatch.netwatch_api.activity.NetworkEventRepository;
import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.reports.ReportPeriod.Preset;
import com.netwatch.netwatch_api.security.RuleType;
import com.netwatch.netwatch_api.security.SecurityAlertRepository;
import com.netwatch.netwatch_api.security.SecurityRuleService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReportService service;

    @Autowired
    ReportRepository reports;

    @Autowired
    DeviceService deviceService;

    @Autowired
    DeviceRepository devices;

    @Autowired
    NetworkEventRepository events;

    @Autowired
    SecurityAlertRepository alerts;

    @Autowired
    SecurityRuleService rules;

    @BeforeEach
    void setUp() {
        clean();
        for (RuleType type : RuleType.values()) {
            rules.update(type, type != RuleType.OFF_HOURS, type.defaultSeverity(), Map.of());
        }
        deviceService.registerObservation(new DeviceObservation("192.168.0.90", "aa:00:00:00:00:90", "nas", "Synology"));
        deviceService.registerObservation(new DeviceObservation("192.168.0.91", "aa:00:00:00:00:91", null, null));
    }

    @AfterEach
    void clean() {
        reports.deleteAll();
        alerts.deleteAll();
        events.deleteAll();
        devices.deleteAll();
    }

    UUID generate(String body) throws Exception {
        String location = mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void overviewReflectsThePeriod() throws Exception {
        UUID id = generate("{\"type\":\"OVERVIEW\",\"period\":\"LAST_7D\"}");

        mvc.perform(get("/api/reports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.document.title").value("Relatório geral da rede"))
                .andExpect(jsonPath("$.document.sections[0].kpis[?(@.label == 'Dispositivos')].value").value(hasItem("2")))
                .andExpect(jsonPath("$.document.sections[0].kpis[?(@.label == 'Novos no período')].value").value(hasItem("2")))
                .andExpect(jsonPath("$.document.sections[0].kpis[?(@.label == 'Alertas no período')].value").value(hasItem("2")))
                .andExpect(jsonPath("$.document.sections[0].summary").value(org.hamcrest.Matchers.containsString("2 dispositivos novos apareceram e 2 alertas foram abertos")))
                .andExpect(jsonPath("$.document.sections[?(@.title == 'Dispositivos novos')].tables[0].rows.length()").value(hasItem(2)));
    }

    @Test
    void everyTypeIsGeneratedAndExported() throws Exception {
        for (ReportType type : ReportType.values()) {
            UUID id = generate("{\"type\":\"" + type + "\",\"period\":\"LAST_30D\"}");

            mvc.perform(get("/api/reports/{id}", id))
                    .andExpect(jsonPath("$.document.title").value(type.title()))
                    .andExpect(jsonPath("$.document.sections").isNotEmpty());

            byte[] pdf = mvc.perform(get("/api/reports/{id}/pdf", id))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

            byte[] csv = mvc.perform(get("/api/reports/{id}/csv", id))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(csv[0]).isEqualTo((byte) 0xEF);
        }
        String inventory = new String(mvc.perform(get("/api/reports").param("type", "INVENTORY"))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(inventory).contains("Inventário de dispositivos");
        mvc.perform(get("/api/reports")).andExpect(jsonPath("$.totalElements").value(ReportType.values().length));
    }

    @Test
    void inventoryListsDevicesAndSecurityListsAlerts() throws Exception {
        UUID inventory = generate("{\"type\":\"INVENTORY\",\"period\":\"LAST_24H\"}");
        mvc.perform(get("/api/reports/{id}", inventory))
                .andExpect(jsonPath("$.document.sections[1].tables[0].totalRows").value(2))
                .andExpect(jsonPath("$.document.sections[1].tables[0].rows[0][0]").value("nas"))
                .andExpect(jsonPath("$.document.sections[1].tables[0].rows[0][3]").value("Synology"));

        UUID security = generate("{\"type\":\"SECURITY\",\"period\":\"LAST_24H\"}");
        mvc.perform(get("/api/reports/{id}", security))
                .andExpect(jsonPath("$.document.sections[0].kpis[0].value").value("2"))
                .andExpect(jsonPath("$.document.sections[1].tables[0].rows.length()").value(2));
    }

    @Test
    void customPeriodValidationAndDeletion() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OVERVIEW\",\"period\":\"CUSTOM\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"LAST_7D\"}"))
                .andExpect(status().isBadRequest());
        UUID id = generate("{\"type\":\"EXPOSURE\",\"period\":\"CUSTOM\",\"from\":\"2026-01-01\",\"to\":\"2026-01-31\"}");
        mvc.perform(get("/api/reports/{id}", id))
                .andExpect(jsonPath("$.report.periodLabel").value("01/01/2026 a 31/01/2026"));
        mvc.perform(delete("/api/reports/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/reports/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void scheduledReportIsNotDuplicated() {
        assertThat(service.scheduled(Preset.PREVIOUS_WEEK)).isNotNull();
        assertThat(service.scheduled(Preset.PREVIOUS_WEEK)).isNull();
        assertThat(reports.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getTrigger()).isEqualTo(Report.Trigger.SCHEDULED));
    }
}
