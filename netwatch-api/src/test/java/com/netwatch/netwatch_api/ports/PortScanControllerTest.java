package com.netwatch.netwatch_api.ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;

/**
 * Verificação de portas com rede simulada. Sem @Transactional: a verificação roda em outra thread.
 */
@SpringBootTest(properties = "netwatch.discovery.authorized-networks=192.168.50.0/24")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortScanControllerTest {

    /** Estado simulado de cada porta; portas ausentes = FILTERED. */
    static final Map<Integer, PortState> NETWORK = new ConcurrentHashMap<>();
    static volatile CountDownLatch gate;

    @TestConfiguration
    static class FakePorts {
        @Bean
        @Primary
        PortProber fakePortProber() {
            return (ip, port) -> {
                CountDownLatch g = gate;
                if (g != null) {
                    try {
                        g.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return NETWORK.getOrDefault(port, PortState.FILTERED);
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    PortScanService service;

    @Autowired
    DeviceService deviceService;

    @Autowired
    DeviceRepository devices;

    @Autowired
    PortScanRepository scans;

    Device device;

    @BeforeEach
    void setUp() {
        gate = null;
        NETWORK.clear();
        scans.deleteAll();
        devices.deleteAll();
        device = deviceService.registerObservation(
                new DeviceObservation("192.168.50.10", "aa:00:00:00:00:10", "nas", null)).device();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        CountDownLatch g = gate;
        if (g != null) {
            g.countDown();
        }
        gate = null;
        awaitIdle(device.getId());
        devices.deleteAll();
    }

    @Test
    void commonScanStoresResultsAndCurrentState() throws Exception {
        NETWORK.put(22, PortState.OPEN);
        NETWORK.put(80, PortState.OPEN);
        NETWORK.put(443, PortState.CLOSED);

        UUID scanId = startScan("{\"type\":\"COMMON\"}");
        awaitIdle(device.getId());

        mvc.perform(get("/api/port-scans/{id}", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.portCount").value(KnownServices.COMMON_PORTS.size()))
                .andExpect(jsonPath("$.openCount").value(2))
                .andExpect(jsonPath("$.closedCount").value(1))
                .andExpect(jsonPath("$.results[?(@.port == 22)].service").value("SSH"))
                .andExpect(jsonPath("$.results[?(@.port == 22)].newlyOpened").value(false));

        mvc.perform(get("/api/devices/{id}/ports", device.getId()))
                .andExpect(jsonPath("$.scanning").value(false))
                .andExpect(jsonPath("$.lastScan.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ports[?(@.port == 80)].state").value("OPEN"));

        mvc.perform(get("/api/ports").param("state", "OPEN"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].port").value(22))
                .andExpect(jsonPath("$.content[0].deviceName").value("nas"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("192.168.50.10"));

        mvc.perform(get("/api/ports").param("q", "https"))
                .andExpect(jsonPath("$.totalElements").value(2)); // 443 HTTPS e 8443 HTTPS alternativo
        mvc.perform(get("/api/ports").param("q", "https").param("state", "CLOSED"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/ports/services"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].devices").value(1));
    }

    @Test
    void secondScanFlagsNewlyOpenedPorts() throws Exception {
        NETWORK.put(3389, PortState.CLOSED);
        startScan("{\"type\":\"CUSTOM\",\"ports\":\"3389,8000-8001\"}");
        awaitIdle(device.getId());

        NETWORK.put(3389, PortState.OPEN);
        UUID second = startScan("{\"type\":\"CUSTOM\",\"ports\":\"3389\"}");
        awaitIdle(device.getId());

        mvc.perform(get("/api/port-scans/{id}", second))
                .andExpect(jsonPath("$.results[0].port").value(3389))
                .andExpect(jsonPath("$.results[0].state").value("OPEN"))
                .andExpect(jsonPath("$.results[0].previousState").value("CLOSED"))
                .andExpect(jsonPath("$.results[0].newlyOpened").value(true));

        mvc.perform(get("/api/devices/{id}/port-scans", device.getId()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].requestedPorts").value("3389"));

        // A porta que passou a aceitar conexões vira evento importante no histórico do dispositivo.
        mvc.perform(get("/api/devices/{id}/events", device.getId()))
                .andExpect(jsonPath("$.content[?(@.type == 'PORT_OPENED')].severity").value(org.hamcrest.Matchers.hasItem("WARNING")))
                .andExpect(jsonPath("$.content[?(@.type == 'PORT_OPENED')].title")
                        .value(org.hamcrest.Matchers.hasItem("Porta 3389 (RDP (Área de Trabalho Remota)) passou a aceitar conexões em 192.168.50.10")))
                .andExpect(jsonPath("$.content[?(@.type == 'PORT_SCAN_COMPLETED')]").isNotEmpty());
    }

    @Test
    void rejectsDeviceOutsideAuthorizedNetworks() throws Exception {
        Device outside = deviceService.registerObservation(
                new DeviceObservation("10.0.0.20", "aa:00:00:00:00:20", null, null)).device();

        mvc.perform(post("/api/devices/{id}/port-scans", outside.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O dispositivo 10.0.0.20 não está em uma rede autorizada para verificação."));
    }

    @Test
    void rejectsInvalidCustomPorts() throws Exception {
        mvc.perform(post("/api/devices/{id}/port-scans", device.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CUSTOM\",\"ports\":\"1-5000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsConcurrentScanOnSameDevice() throws Exception {
        gate = new CountDownLatch(1);
        startScan(null);

        mvc.perform(get("/api/devices/{id}/ports", device.getId())).andExpect(jsonPath("$.scanning").value(true));
        mvc.perform(post("/api/devices/{id}/port-scans", device.getId())).andExpect(status().isConflict());
    }

    @Test
    void returns404ForUnknownDeviceOrScan() throws Exception {
        mvc.perform(post("/api/devices/{id}/port-scans", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/port-scans/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void deletingDeviceRemovesItsPortData() throws Exception {
        NETWORK.put(22, PortState.OPEN);
        startScan(null);
        awaitIdle(device.getId());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/devices/{id}", device.getId())).andExpect(status().isNoContent());

        assertThat(scans.count()).isZero();
        mvc.perform(get("/api/ports")).andExpect(jsonPath("$.totalElements").value(0));
    }

    private UUID startScan(String body) throws Exception {
        var request = post("/api/devices/{id}/port-scans", device.getId());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mvc.perform(request)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(response.substring(response.indexOf("\"id\":\"") + 6, response.indexOf("\"id\":\"") + 42));
    }

    private void awaitIdle(UUID deviceId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (service.isScanning(deviceId) && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
    }
}
