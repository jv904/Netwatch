package com.netwatch.netwatch_api.traffic.connections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.traffic.TrafficProtocol;

class NetstatParserTest {

    /** Saída real de um Windows em português (cabeçalho traduzido, dados em inglês). */
    static final String NETSTAT = """

            Conexões ativas

              Proto  Endereço local         Endereço externo       Estado           PID
              TCP    0.0.0.0:135            0.0.0.0:0              LISTENING       2356
              TCP    192.168.100.20:50123   142.250.79.46:443      ESTABLISHED     8812
              TCP    192.168.100.20:445     192.168.100.31:51000   ESTABLISHED     4
              TCP    127.0.0.1:5432         127.0.0.1:60001        TIME_WAIT       0
              TCP    [::]:135               [::]:0                 LISTENING       2356
              UDP    0.0.0.0:5353           *:*                                    41204
              UDP    192.168.100.20:60000   192.168.100.1:53                       1200
              UDP    [::]:5353              *:*                                    41204
            """;

    @Test
    void readsIpv4ConnectionsAndIgnoresHeaderAndIpv6() {
        List<ConnectionEntry> entries = NetstatParser.parse(NETSTAT);

        assertThat(entries).hasSize(6);
        assertThat(entries).noneMatch(e -> e.localIp().contains(":") || e.localIp().contains("["));
    }

    @Test
    void parsesTcpFields() {
        ConnectionEntry https = NetstatParser.parse(NETSTAT).get(1);

        assertThat(https.protocol()).isEqualTo(TrafficProtocol.TCP);
        assertThat(https.localIp()).isEqualTo("192.168.100.20");
        assertThat(https.localPort()).isEqualTo(50123);
        assertThat(https.remoteIp()).isEqualTo("142.250.79.46");
        assertThat(https.remotePort()).isEqualTo(443);
        assertThat(https.state()).isEqualTo("ESTABLISHED");
        assertThat(https.pid()).isEqualTo(8812);
    }

    @Test
    void listeningAndUnboundUdpHaveNoRemote() {
        List<ConnectionEntry> entries = NetstatParser.parse(NETSTAT);

        ConnectionEntry listening = entries.get(0);
        assertThat(listening.isListening()).isTrue();
        assertThat(listening.remoteIp()).isNull();

        ConnectionEntry mdns = entries.get(4);
        assertThat(mdns.protocol()).isEqualTo(TrafficProtocol.UDP);
        assertThat(mdns.remoteIp()).isNull();
        assertThat(mdns.state()).isNull();
        assertThat(mdns.pid()).isEqualTo(41204);

        ConnectionEntry dns = entries.get(5);
        assertThat(dns.remoteIp()).isEqualTo("192.168.100.1");
        assertThat(dns.remotePort()).isEqualTo(53);
    }

    @Test
    void toleratesEmptyAndGarbage() {
        assertThat(NetstatParser.parse(null)).isEmpty();
        assertThat(NetstatParser.parse("")).isEmpty();
        assertThat(NetstatParser.parse("TCP lixo\nUDP a b c d")).isEmpty();
    }

    @Test
    void parsesTasklistCsv() {
        Map<Integer, String> processes = TasklistParser.parse("""
                "System Idle Process","0","Services","0","8 K"
                "chrome.exe","8812","Console","1","150.000 K"
                "svchost.exe","1200","Services","0","12.000 K"
                lixo
                """);

        assertThat(processes).containsEntry(8812, "chrome.exe").containsEntry(1200, "svchost.exe").hasSize(3);
        assertThat(TasklistParser.parse(null)).isEmpty();
    }
}
