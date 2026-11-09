package com.netwatch.netwatch_api.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.device.DeviceService;
import com.netwatch.netwatch_api.device.DeviceStatus;
import com.netwatch.netwatch_api.discovery.probe.ArpTable;
import com.netwatch.netwatch_api.discovery.probe.HostProber;
import com.netwatch.netwatch_api.discovery.probe.HostnameResolver;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces;

/**
 * Fluxo completo da descoberta com a rede simulada (nenhum pacote real é enviado).
 * Sem @Transactional: a descoberta roda em outra thread e precisa ver os dados confirmados.
 */
@SpringBootTest(properties = "netwatch.discovery.authorized-networks=192.168.50.0/24")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscoveryControllerTest {

    /** Quando definido, a sondagem fica bloqueada até o teste liberar. */
    static volatile CountDownLatch gate;

    @TestConfiguration
    static class FakeNetwork {

        @Bean
        @Primary
        HostProber fakeProber() {
            return ip -> {
                CountDownLatch g = gate;
                if (g != null) {
                    try {
                        g.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return ip.equals("192.168.50.1");
            };
        }

        @Bean
        @Primary
        ArpTable fakeArp() {
            return () -> Map.of(
                    "192.168.50.1", "3c:5a:b4:00:00:01",
                    "192.168.50.77", "da:a1:19:00:00:02");
        }

        @Bean
        @Primary
        HostnameResolver fakeResolver() {
            return ip -> Optional.empty();
        }

        @Bean
        @Primary
        LocalInterfaces fakeInterfaces() {
            return List::of;
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    DiscoveryService discoveryService;

    @Autowired
    DiscoveryRunRepository runs;

    @Autowired
    DeviceRepository devices;

    @Autowired
    DeviceService deviceService;

    @BeforeEach
    void clean() {
        gate = null;
        runs.deleteAll();
        devices.deleteAll();
    }

    @AfterEach
    void release() {
        CountDownLatch g = gate;
        if (g != null) {
            g.countDown();
        }
        gate = null;
        awaitIdle();
        runs.deleteAll();
        devices.deleteAll();
    }

    @Test
    void listsAuthorizedNetworks() throws Exception {
        mvc.perform(get("/api/discovery/networks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cidr").value("192.168.50.0/24"))
                .andExpect(jsonPath("$[0].hostCount").value(254));
    }

    @Test
    void discoveryRegistersDevicesAndInactivatesMissingOnes() throws Exception {
        // Dispositivo visto antes, que não aparece mais na rede.
        Device gone = deviceService.registerObservation(
                new DeviceObservation("192.168.50.200", "aa:00:00:00:00:99", null, null)).device();
        // Dispositivo de outra rede: não deve ser afetado.
        deviceService.registerObservation(new DeviceObservation("10.0.0.5", "aa:00:00:00:00:98", null, null));

        String body = mvc.perform(post("/api/discovery/runs"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.cidr").value("192.168.50.0/24"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        DiscoveryRun run = awaitFinished(id);
        assertThat(run.getStatus()).isEqualTo(DiscoveryRunStatus.COMPLETED);
        assertThat(run.getHostsScanned()).isEqualTo(254);
        assertThat(run.getDevicesFound()).isEqualTo(2);
        assertThat(run.getNewDevices()).isEqualTo(2);
        assertThat(run.getInactivated()).isEqualTo(1);

        assertThat(devices.findByMacAddress("3c:5a:b4:00:00:01")).get()
                .satisfies(d -> {
                    assertThat(d.isRecognized()).isFalse();
                    assertThat(d.getVendor()).isEqualTo("Google, Inc.");
                    assertThat(d.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
                });
        assertThat(devices.findById(gone.getId())).get()
                .extracting(Device::getStatus).isEqualTo(DeviceStatus.INACTIVE);
        assertThat(devices.findByMacAddress("aa:00:00:00:00:98")).get()
                .extracting(Device::getStatus).isEqualTo(DeviceStatus.ACTIVE);

        mvc.perform(get("/api/discovery/runs/{id}", id))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.durationMs").isNumber());

        // Segunda execução: nada novo, sem duplicados.
        DiscoveryRun second = awaitFinished(discoveryService.start(null, DiscoveryTrigger.MANUAL).getId());
        assertThat(second.getNewDevices()).isZero();
        assertThat(devices.count()).isEqualTo(4);
    }

    @Test
    void rejectsOutOfScopeNetwork() throws Exception {
        mvc.perform(post("/api/discovery/runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cidr\":\"192.168.60.0/24\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A faixa 192.168.60.0/24 não está dentro das redes autorizadas."));
    }

    @Test
    void rejectsConcurrentDiscovery() throws Exception {
        gate = new CountDownLatch(1);
        mvc.perform(post("/api/discovery/runs")).andExpect(status().isAccepted());

        mvc.perform(get("/api/discovery/runs/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(post("/api/discovery/runs")).andExpect(status().isConflict());

        gate.countDown();
        awaitIdle();
        mvc.perform(get("/api/discovery/runs/current")).andExpect(status().isNoContent());
    }

    private DiscoveryRun awaitFinished(UUID id) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            DiscoveryRun run = runs.findById(id).orElseThrow();
            if (run.getStatus() != DiscoveryRunStatus.RUNNING && !discoveryService.isRunning()) {
                return run;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("A descoberta não terminou a tempo");
    }

    private void awaitIdle() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline) && discoveryService.isRunning()) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
