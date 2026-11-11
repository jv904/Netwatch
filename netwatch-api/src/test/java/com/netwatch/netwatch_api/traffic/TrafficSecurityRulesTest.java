package com.netwatch.netwatch_api.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.netwatch.netwatch_api.security.AlertSeverity;
import com.netwatch.netwatch_api.security.RuleType;
import com.netwatch.netwatch_api.security.SecurityAlert;
import com.netwatch.netwatch_api.security.SecurityAlertRepository;
import com.netwatch.netwatch_api.security.SecurityEngine;
import com.netwatch.netwatch_api.security.SecurityRuleService;

/** Regras do motor de segurança sobre os fluxos de tráfego gravados. */
@SpringBootTest(properties = "netwatch.discovery.authorized-networks=192.168.50.0/24")
@ActiveProfiles("test")
class TrafficSecurityRulesTest {

    @Autowired
    SecurityEngine engine;

    @Autowired
    SecurityRuleService rules;

    @Autowired
    SecurityAlertRepository alerts;

    @Autowired
    TrafficFlowRepository flows;

    @Autowired
    CaptureSessionRepository sessions;

    CaptureSession session;

    @BeforeEach
    void setUp() {
        clean();
        for (RuleType type : RuleType.values()) {
            rules.update(type, type != RuleType.OFF_HOURS, type.defaultSeverity(), Map.of());
        }
        Instant now = Instant.now();
        session = sessions.save(new CaptureSession(CaptureMode.CONNECTIONS, "connections", "teste", "192.168.50.5",
                "192.168.50.0/24", now, now.plusSeconds(600)));
    }

    @AfterEach
    void clean() {
        alerts.deleteAll();
        flows.deleteAll();
        sessions.deleteAll();
    }

    void flow(TrafficProtocol protocol, String src, int sport, String dst, int dport, String process) {
        TrafficFlow f = new TrafficFlow(session.getId(), protocol, src, sport, dst, dport, Instant.now());
        f.describeConnection(process, "ESTABLISHED");
        flows.save(f);
    }

    List<SecurityAlert> alertsOf(RuleType rule) {
        return alerts.findAll().stream().filter(a -> a.getRule() == rule).toList();
    }

    @Test
    void detectsPortScanAndNetworkSweep() {
        for (int port = 1; port <= 25; port++) {
            flow(TrafficProtocol.TCP, "192.168.50.66", 40000 + port, "192.168.50.10", port, null);
        }
        for (int host = 100; host < 135; host++) {
            flow(TrafficProtocol.TCP, "192.168.50.77", 50000 + host, "192.168.50." + host, 445, null);
        }
        // Uso normal: muitas conexões para a internet não é varredura da rede.
        for (int i = 0; i < 40; i++) {
            flow(TrafficProtocol.TCP, "192.168.50.5", 60000 + i, "142.250.0." + i, 443, "chrome.exe");
        }

        engine.evaluateTraffic();

        List<SecurityAlert> scans = alertsOf(RuleType.PORT_SCAN);
        assertThat(scans).extracting(SecurityAlert::getTitle).containsExactlyInAnyOrder(
                "Varredura de portas: 192.168.50.66 tentou 25 portas de 192.168.50.10",
                "Varredura da rede: 192.168.50.77 contatou 35 equipamentos");
    }

    @Test
    void detectsSuspiciousPortsAndExternalAdminAccessOnce() {
        flow(TrafficProtocol.TCP, "192.168.50.5", 51000, "45.1.2.3", 4444, "malware.exe");
        flow(TrafficProtocol.TCP, "200.1.2.3", 50000, "192.168.50.5", 3389, "svchost.exe");
        flow(TrafficProtocol.TCP, "192.168.50.5", 51001, "8.8.8.8", 443, "chrome.exe");

        engine.evaluateTraffic();
        engine.evaluateTraffic();

        assertThat(alertsOf(RuleType.SUSPICIOUS_PORT)).singleElement().satisfies(a -> {
            assertThat(a.getTitle()).isEqualTo("Conexão em porta suspeita: 192.168.50.5 → 45.1.2.3:4444");
            assertThat(a.getEvidence()).contains("malware.exe");
            assertThat(a.getSeverity()).isEqualTo(AlertSeverity.HIGH);
            assertThat(a.getOccurrences()).isEqualTo(1);
        });
        assertThat(alertsOf(RuleType.EXTERNAL_ADMIN_ACCESS)).singleElement()
                .satisfies(a -> assertThat(a.getTitle())
                        .isEqualTo("Acesso de fora da rede a 3389 (RDP (Área de Trabalho Remota)) em 192.168.50.5"));
    }
}
