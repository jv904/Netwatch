package com.netwatch.netwatch_api.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import com.netwatch.netwatch_api.discovery.probe.ArpTable;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces.LocalAddress;
import com.netwatch.netwatch_api.traffic.capture.PacketMeta;
import com.netwatch.netwatch_api.traffic.capture.PacketSource;
import com.netwatch.netwatch_api.traffic.connections.ConnectionEntry;
import com.netwatch.netwatch_api.traffic.connections.ConnectionTable;

/**
 * Sem driver de captura: o modo automático cai para a tabela de conexões do sistema (simulada aqui).
 */
@SpringBootTest(properties = {
        "netwatch.discovery.authorized-networks=192.168.50.0/24",
        "netwatch.capture.flush-interval=1s",
        "netwatch.capture.connection-poll-interval=1s" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConnectionCaptureTest {

    static final List<ConnectionEntry> TABLE = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class FakeSystem {
        @Bean
        @Primary
        PacketSource noDriver() {
            return new PacketSource() {
                @Override
                public SourceStatus status() {
                    return SourceStatus.unavailable("Npcap não encontrado.");
                }

                @Override
                public List<CaptureInterface> interfaces() {
                    return List.of();
                }

                @Override
                public CaptureStats capture(String i, String f, int s, boolean p, Consumer<PacketMeta> sink,
                        BooleanSupplier stop) {
                    throw new CaptureException("sem driver");
                }
            };
        }

        @Bean
        @Primary
        ConnectionTable fakeConnectionTable() {
            return new ConnectionTable() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public List<ConnectionEntry> snapshot() {
                    return List.copyOf(TABLE);
                }
            };
        }

        @Bean
        @Primary
        LocalInterfaces fakeInterfaces() {
            return () -> List.of(new LocalAddress("eth0", "Placa de teste", "192.168.50.5", 24, "aa:00:00:00:00:05",
                    false));
        }

        @Bean
        @Primary
        ArpTable fakeArp() {
            return () -> Map.of("192.168.50.31", "aa:00:00:00:00:31", "192.168.50.1", "aa:00:00:00:00:01");
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
        TABLE.clear();
        clean();
    }

    @AfterEach
    void tearDown() throws Exception {
        capture.active().ifPresent(s -> capture.stop(s.getId()));
        awaitIdle();
        clean();
    }

    void clean() {
        flows.deleteAll();
        stats.deleteAll();
        sessions.deleteAll();
        devices.deleteAll();
    }

    static ConnectionEntry tcp(String local, int lport, String remote, Integer rport, String state, String process) {
        return new ConnectionEntry(TrafficProtocol.TCP, local, lport, remote, rport, state, 100, process);
    }

    @Test
    void fallsBackToConnectionTableWithoutDriver() throws Exception {
        mvc.perform(get("/api/capture/status"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.mode").value("CONNECTIONS"))
                .andExpect(jsonPath("$.packetCaptureAvailable").value(false))
                .andExpect(jsonPath("$.connectionTableAvailable").value(true))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("Npcap não encontrado")));
        mvc.perform(get("/api/capture/interfaces")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recordsConnectionsWithDirectionProcessAndDevices() throws Exception {
        TABLE.add(tcp("0.0.0.0", 445, null, null, "LISTENING", "System"));
        // Saída para a internet (local autorizado) e para um vizinho da rede.
        TABLE.add(tcp("192.168.50.5", 50123, "142.250.79.46", 443, "ESTABLISHED", "chrome.exe"));
        TABLE.add(tcp("192.168.50.5", 50200, "192.168.50.1", 80, "ESTABLISHED", "chrome.exe"));
        // Entrada: alguém da rede acessando o compartilhamento desta máquina.
        TABLE.add(tcp("192.168.50.5", 445, "192.168.50.31", 51000, "ESTABLISHED", "System"));
        // Fora do escopo: loopback.
        TABLE.add(tcp("127.0.0.1", 5432, "127.0.0.1", 60001, "ESTABLISHED", "postgres.exe"));

        String location = mvc.perform(post("/api/capture/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMinutes\":5}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("CONNECTIONS"))
                .andExpect(jsonPath("$.localAddress").value("192.168.50.5"))
                .andReturn().getResponse().getHeader("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        awaitFlows(3);
        mvc.perform(post("/api/capture/sessions/{id}/stop", id)).andExpect(status().isAccepted());
        awaitIdle();

        mvc.perform(get("/api/capture/sessions/{id}", id))
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.mode").value("CONNECTIONS"))
                .andExpect(jsonPath("$.newDevices").value(2));

        mvc.perform(get("/api/traffic/flows"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.dstPort == 443)].processName").value(hasItem("chrome.exe")))
                .andExpect(jsonPath("$.content[?(@.dstPort == 443)].srcIp").value(hasItem("192.168.50.5")))
                .andExpect(jsonPath("$.content[?(@.dstPort == 443)].connectionState").value(hasItem("ESTABLISHED")))
                // Entrada: o vizinho é a origem e a porta 445 local é o destino.
                .andExpect(jsonPath("$.content[?(@.dstPort == 445)].srcIp").value(hasItem("192.168.50.31")))
                .andExpect(jsonPath("$.content[?(@.dstPort == 445)].service").value(hasItem("SMB (compartilhamento Windows)")))
                .andExpect(jsonPath("$.content[?(@.dstPort == 445)].srcDeviceId").value(hasItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$.content[?(@.srcIp == '127.0.0.1')]").isEmpty());

        mvc.perform(get("/api/traffic/summary").param("window", "1h"))
                .andExpect(jsonPath("$.totals.bytes").value(0))
                .andExpect(jsonPath("$.totals.flows").value(3))
                .andExpect(jsonPath("$.totals.connections").value(3))
                .andExpect(jsonPath("$.topHosts[0].ip").value("192.168.50.5"))
                .andExpect(jsonPath("$.topHosts[0].flows").value(3));

        assertThat(devices.findByMacAddress("aa:00:00:00:00:31")).isPresent();
    }

    void awaitFlows(long count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (flows.count() < count && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(flows.count()).isGreaterThanOrEqualTo(count);
    }

    void awaitIdle() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (capture.active().isPresent() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(capture.active()).isEmpty();
    }
}
