package com.netwatch.netwatch_api.device;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeviceControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DeviceService service;

    @Test
    void createsDevice() throws Exception {
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content("""
                        {"ipAddress":"192.168.0.10","macAddress":"AA:BB:CC:DD:EE:01",
                         "customName":"Notebook da Ana","type":"NOTEBOOK"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/devices/")))
                .andExpect(jsonPath("$.displayName").value("Notebook da Ana"))
                .andExpect(jsonPath("$.macAddress").value("aa:bb:cc:dd:ee:01"))
                .andExpect(jsonPath("$.recognized").value(true))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void rejectsDuplicateMac() throws Exception {
        service.registerObservation(new DeviceObservation("192.168.0.10", "aa:bb:cc:dd:ee:01", null, null));

        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipAddress\":\"192.168.0.11\",\"macAddress\":\"aa-bb-cc-dd-ee-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    void rejectsInvalidIp() throws Exception {
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipAddress\":\"999.1.1.1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Endereço IP inválido: 999.1.1.1"));
    }

    @Test
    void reportsFieldValidationErrors() throws Exception {
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ipAddress").exists());
    }

    @Test
    void listsAndFiltersUnknownDevices() throws Exception {
        service.registerObservation(new DeviceObservation("192.168.0.25", "aa:00:00:00:00:01", null, null));
        Device known = service.registerObservation(
                new DeviceObservation("192.168.0.15", "aa:00:00:00:00:02", "desktop", null)).device();
        service.setRecognized(known.getId(), true);

        mvc.perform(get("/api/devices").param("recognized", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ipAddress").value("192.168.0.25"))
                .andExpect(jsonPath("$.content[0].displayName").value("Dispositivo desconhecido"));

        mvc.perform(get("/api/devices").param("q", "desk"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].hostname").value("desktop"));

        mvc.perform(get("/api/devices/summary"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.active").value(2))
                .andExpect(jsonPath("$.unrecognized").value(1));
    }

    @Test
    void marksDeviceAsRecognized() throws Exception {
        Device device = service.registerObservation(
                new DeviceObservation("192.168.0.25", "aa:00:00:00:00:03", null, null)).device();

        mvc.perform(patch("/api/devices/{id}/recognition", device.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recognized\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognized").value(true));
    }

    @Test
    void updatesUserControlledFields() throws Exception {
        Device device = service.registerObservation(
                new DeviceObservation("192.168.0.25", "aa:00:00:00:00:04", null, null)).device();

        mvc.perform(put("/api/devices/{id}", device.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"customName":"TV da sala","type":"IOT","recognized":true,"notes":"Smart TV"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("TV da sala"))
                .andExpect(jsonPath("$.type").value("IOT"))
                .andExpect(jsonPath("$.notes").value("Smart TV"));
    }

    @Test
    void returns404ForMissingDevice() throws Exception {
        mvc.perform(get("/api/devices/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void deletesDevice() throws Exception {
        Device device = service.registerObservation(
                new DeviceObservation("192.168.0.50", null, null, null)).device();

        mvc.perform(delete("/api/devices/{id}", device.getId())).andExpect(status().isNoContent());
        mvc.perform(get("/api/devices/{id}", device.getId())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnknownSortProperty() throws Exception {
        mvc.perform(get("/api/devices").param("sort", "doesNotExist"))
                .andExpect(status().isBadRequest());
    }
}
