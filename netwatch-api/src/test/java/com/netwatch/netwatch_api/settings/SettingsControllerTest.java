package com.netwatch.netwatch_api.settings;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest(properties = {
        "netwatch.discovery.authorized-networks=192.168.70.0/24",
        "netwatch.capture.max-duration=8h" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppSettingRepository repository;

    @Autowired
    SettingsService settings;

    @Autowired
    NetworkEventRepository events;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
        settings.reload();
        events.deleteAll();
    }

    void putSettings(String json, int expected) throws Exception {
        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected));
    }

    @Test
    void listsDefaultsFromConfiguration() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SettingKey.values().length))
                .andExpect(jsonPath("$[?(@.key == 'discovery.authorized-networks')].value").value(hasItem("192.168.70.0/24")))
                .andExpect(jsonPath("$[?(@.key == 'discovery.authorized-networks')].custom").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.key == 'capture.max-duration-minutes')].value").value(hasItem("480")))
                .andExpect(jsonPath("$[?(@.key == 'ports.timeout-ms')].min").value(hasItem(200)))
                .andExpect(jsonPath("$[?(@.key == 'capture.mode')].options[*]").value(hasItem("connections")));
    }

    @Test
    void changesApplyImmediately() throws Exception {
        putSettings("{\"discovery.authorized-networks\":\"10.20.0.9/24, 10.20.0.0/24 192.168.71.0/24\","
                + "\"capture.enabled\":\"false\"}", 200);

        mvc.perform(get("/api/discovery/networks"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cidr").value("10.20.0.0/24"))
                .andExpect(jsonPath("$[1].cidr").value("192.168.71.0/24"));
        mvc.perform(get("/api/capture/status"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/settings"))
                .andExpect(jsonPath("$[?(@.key == 'discovery.authorized-networks')].value")
                        .value(hasItem("10.20.0.0/24,192.168.71.0/24")))
                .andExpect(jsonPath("$[?(@.key == 'capture.enabled')].custom").value(hasItem(true)));

        mvc.perform(get("/api/events").param("type", "SETTINGS_CHANGED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("2 configurações alteradas"));
    }

    @Test
    void invalidBatchChangesNothing() throws Exception {
        putSettings("{\"ports.timeout-ms\":\"3000\",\"discovery.authorized-networks\":\"8.8.8.0/24\"}", 400);
        putSettings("{\"ports.timeout-ms\":\"50\"}", 400);
        putSettings("{\"capture.mode\":\"magic\"}", 400);
        putSettings("{\"reports.weekly\":\"talvez\"}", 400);
        putSettings("{\"nao.existe\":\"1\"}", 400);
        putSettings("{\"capture.default-duration-minutes\":\"600\"}", 400);
        putSettings("{}", 400);

        mvc.perform(get("/api/settings"))
                .andExpect(jsonPath("$[?(@.custom == true)]").isEmpty());
    }

    @Test
    void restoreAndDefaultValueRemoveCustomization() throws Exception {
        putSettings("{\"ports.timeout-ms\":\"3000\",\"activity.retention-days\":\"30\"}", 200);
        mvc.perform(delete("/api/settings/{key}", "ports.timeout-ms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'ports.timeout-ms')].custom").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.key == 'ports.timeout-ms')].value").value(hasItem("1500")));
        // Informar o valor de fábrica também remove a personalização.
        putSettings("{\"activity.retention-days\":\"90\"}", 200);
        mvc.perform(get("/api/settings")).andExpect(jsonPath("$[?(@.custom == true)]").isEmpty());
        mvc.perform(delete("/api/settings/{key}", "nao.existe")).andExpect(status().isBadRequest());
    }

    @Test
    void systemInformation() throws Exception {
        mvc.perform(get("/api/settings/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.javaVersion").isNotEmpty())
                .andExpect(jsonPath("$.database").value(org.hamcrest.Matchers.startsWith("H2")))
                .andExpect(jsonPath("$.authorizedNetworks[0].cidr").value("192.168.70.0/24"))
                .andExpect(jsonPath("$.networksConfigured").value(true))
                .andExpect(jsonPath("$.data.devices").isNumber());
    }
}
