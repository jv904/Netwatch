package com.netwatch.netwatch_api.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.traffic.capture.PacketMeta;
import com.netwatch.netwatch_api.traffic.capture.PacketSource;

/**
 * Captura com uma fonte de pacotes simulada. Sem @Transactional: a captura roda em outra thread.
 */
@SpringBootTest(properties = {
        "netwatch.discovery.authorized-networks=192.168.50.0/24",
        "netwatch.capture.enabled=true",
        "netwatch.capture.mode=packets",
        "netwatch.capture.flush-interval=1s" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaptureControllerTest {

    static final BlockingQueue<PacketMeta> WIRE = new LinkedBlockingQueue<>();
    static volatile boolean driverInstalled = true;

    @TestConfiguration
    static class FakeDriver {
        @Bean
        @Primary
        PacketSource fakePacketSource() {
            return new PacketSource() {
                @Override
                public SourceStatus status() {
                    return driverInstalled ? SourceStatus.available("Npcap simulado 1.0")
                            : SourceStatus.unavailable("Npcap não encontrado.");
                }

                @Override
                public List<CaptureInterface> interfaces() {
                    return List.of(
                            new CaptureInterface("\\Device\\NPF_{TESTE}", "Placa de teste",
                                    List.of(new InterfaceAddress("192.168.50.5", 24)), "aa:00:00:00:00:05", false),
                            new CaptureInterface("\\Device\\NPF_{OUTRA}", "Rede de fora",
                                    List.of(new InterfaceAddress("10.9.9.9", 24)), "aa:00:00:00:00:09", false));
                }

                @Override
                public CaptureStats capture(String interfaceName, String bpfFilter, int snaplen, boolean promiscuous,
                        Consumer<PacketMeta> sink, BooleanSupplier stopRequested) {
                    long count = 0;
                    try {
                        while (!stopRequested.getAsBoolean()) {
                            PacketMeta p = WIRE.poll(50, TimeUnit.MILLISECONDS);
                            if (p != null) {
                                sink.accept(p);
                                count++;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    PacketMeta rest;
                    while ((rest = WIRE.poll()) != null) {
                        sink.accept(rest);
                        count++;
                    }
                    return new CaptureStats(count, 0);
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    CaptureService capture;

    @Autowired
    TrafficFlowRepository flows;

    @Autowired
    TrafficMinuteStatRepository stats;

    @Autowired
    CaptureSessionRepository sessions;

    @Autowired
    DeviceRepository devices;

    @BeforeEach
    void setUp() {
        driverInstalled = true;
        WIRE.clear();
        cleanDatabase();
    }

    @AfterEach
    void tearDown() throws Exception {
        capture.active().ifPresent(s -> capture.stop(s.getId()));
        awaitIdle();
        cleanDatabase();
    }

    void cleanDatabase() {
        flows.deleteAll();
        stats.deleteAll();
        sessions.deleteAll();
        devices.deleteAll();
    }

    static PacketMeta packet(TrafficProtocol protocol, String srcMac, String src, Integer sport, String dst,
            Integer dport, int length) {
        return new PacketMeta(Instant.now(), srcMac, "aa:00:00:00:00:99", src, dst, protocol, sport, dport, length,
                0);
    }

    @Test
    void statusShowsDriverAndScope() throws Exception {
        mvc.perform(get("/api/capture/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.libraryVersion").value("Npcap simulado 1.0"))
                .andExpect(jsonPath("$.authorizedNetworks[0]").value("192.168.50.0/24"));
    }

    @Test
    void onlyInterfacesInAuthorizedNetworksAreEligible() throws Exception {
        mvc.perform(get("/api/capture/interfaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eligible").value(true))
                .andExpect(jsonPath("$[0].authorizedAddress").value("192.168.50.5/24"))
                .andExpect(jsonPath("$[1].eligible").value(false));
    }

    @Test
    void captureAggregatesFlowsLinksDevicesAndDropsOutOfScope() throws Exception {
        UUID sessionId = start("{\"durationMinutes\":5}");

        WIRE.add(packet(TrafficProtocol.TCP, "aa:00:00:00:00:10", "192.168.50.10", 50000, "192.168.50.20", 445, 100));
        WIRE.add(packet(TrafficProtocol.TCP, "aa:00:00:00:00:10", "192.168.50.10", 50000, "192.168.50.20", 445, 1500));
        WIRE.add(packet(TrafficProtocol.TCP, "aa:00:00:00:00:10", "192.168.50.10", 50000, "192.168.50.20", 445, 400));
        WIRE.add(packet(TrafficProtocol.UDP, "aa:00:00:00:00:10", "192.168.50.10", 5353, "224.0.0.251", 5353, 80));
        // Fora das redes autorizadas: descartado mesmo que o driver deixe passar.
        WIRE.add(packet(TrafficProtocol.TCP, "aa:00:00:00:00:77", "10.1.1.1", 1234, "10.1.1.2", 80, 999));
        awaitDrained();

        mvc.perform(post("/api/capture/sessions/{id}/stop", sessionId)).andExpect(status().isAccepted());
        awaitIdle();

        mvc.perform(get("/api/capture/sessions/{id}", sessionId))
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.packets").value(4))
                .andExpect(jsonPath("$.bytes").value(2080))
                // .10 (origem) e .20 (destino), os dois na sub-rede local e com MAC conhecido.
                .andExpect(jsonPath("$.newDevices").value(2));

        mvc.perform(get("/api/traffic/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.protocol == 'TCP')].packets").value(hasItem(3)))
                .andExpect(jsonPath("$.content[?(@.protocol == 'TCP')].service").value(hasItem("SMB (compartilhamento Windows)")))
                .andExpect(jsonPath("$.content[?(@.protocol == 'UDP')].service").value(hasItem("mDNS")))
                .andExpect(jsonPath("$.content[0].srcDeviceId").value(notNullValue()));

        mvc.perform(get("/api/traffic/flows").param("protocol", "UDP"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/traffic/flows").param("port", "445").param("window", "1h"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/traffic/flows").param("ip", "10.1.1.1"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/traffic/summary").param("window", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.packets").value(4))
                .andExpect(jsonPath("$.totals.bytes").value(2080))
                .andExpect(jsonPath("$.totals.flows").value(2))
                .andExpect(jsonPath("$.totals.hosts").value(2))
                .andExpect(jsonPath("$.bucketSeconds").value(60))
                .andExpect(jsonPath("$.topHosts[0].ip").value("192.168.50.10"))
                .andExpect(jsonPath("$.topHosts[0].bytesSent").value(2080))
                .andExpect(jsonPath("$.topServices[0].port").value(445));

        UUID deviceId = devices.findByMacAddress("aa:00:00:00:00:10").orElseThrow().getId();
        mvc.perform(get("/api/devices/{id}/traffic", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bytesSent").value(2080))
                .andExpect(jsonPath("$.bytesReceived").value(0))
                .andExpect(jsonPath("$.recentFlows.length()").value(2));
    }

    @Test
    void onlyOneSessionAtATime() throws Exception {
        start(null);
        mvc.perform(post("/api/capture/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void missingDriverAnswers503() throws Exception {
        driverInstalled = false;
        mvc.perform(post("/api/capture/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Npcap não encontrado."));
        mvc.perform(get("/api/capture/interfaces")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void refusesInterfaceOutsideAuthorizedNetworks() throws Exception {
        mvc.perform(post("/api/capture/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"interfaceName\":\"\\\\Device\\\\NPF_{OUTRA}\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesInvalidDurationAndWindow() throws Exception {
        mvc.perform(post("/api/capture/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"durationMinutes\":100000}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/traffic/summary").param("window", "3y"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stoppingASessionThatIsNotRunningIsAConflict() throws Exception {
        UUID id = start(null);
        mvc.perform(post("/api/capture/sessions/{id}/stop", id)).andExpect(status().isAccepted());
        awaitIdle();
        mvc.perform(post("/api/capture/sessions/{id}/stop", id)).andExpect(status().isConflict());
        mvc.perform(post("/api/capture/sessions/{id}/stop", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    UUID start(String body) throws Exception {
        String location = mvc.perform(post("/api/capture/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(body != null ? body : "{}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/capture/sessions/")))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.interfaceDescription").value("Placa de teste"))
                .andExpect(jsonPath("$.filter").value("net 192.168.50.0/24"))
                .andReturn().getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    static void awaitDrained() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!WIRE.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(WIRE).isEmpty();
    }

    void awaitIdle() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (capture.active().isPresent() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(capture.active()).isEmpty();
    }
}
