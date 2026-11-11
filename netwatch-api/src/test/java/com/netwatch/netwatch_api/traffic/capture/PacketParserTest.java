package com.netwatch.netwatch_api.traffic.capture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.traffic.TrafficProtocol;

class PacketParserTest {

    static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    static final HexFormat HEX = HexFormat.of();

    static final String ETH_IPV4 = "ffeeddccbbaa" + "112233445566" + "0800";

    @Test
    void parsesTcpHeadersOnly() {
        // IPv4 (IHL=5, total 52, TCP) 192.168.100.10 -> 192.168.100.20, porta 50000 -> 445, flags SYN
        String ip = "45000034" + "00004000" + "4006" + "0000" + "c0a8640a" + "c0a86414";
        String tcp = "c350" + "01bd" + "00000000" + "00000000" + "5002" + "ffff" + "00000000";
        byte[] frame = HEX.parseHex(ETH_IPV4 + ip + tcp);

        PacketMeta p = PacketParser.parse(frame, 66, NOW).orElseThrow();

        assertThat(p.protocol()).isEqualTo(TrafficProtocol.TCP);
        assertThat(p.srcMac()).isEqualTo("11:22:33:44:55:66");
        assertThat(p.dstMac()).isEqualTo("ff:ee:dd:cc:bb:aa");
        assertThat(p.srcIp()).isEqualTo("192.168.100.10");
        assertThat(p.dstIp()).isEqualTo("192.168.100.20");
        assertThat(p.srcPort()).isEqualTo(50000);
        assertThat(p.dstPort()).isEqualTo(445);
        assertThat(p.tcpFlags()).isEqualTo(0x02);
        assertThat(p.length()).isEqualTo(66);
        assertThat(p.timestamp()).isEqualTo(NOW);
    }

    @Test
    void parsesUdpAndUsesCapturedLengthWhenOriginalIsUnknown() {
        String ip = "4500001c" + "00000000" + "4011" + "0000" + "c0a8640a" + "e00000fb";
        String udp = "14e9" + "14e9" + "0008" + "0000";
        byte[] frame = HEX.parseHex(ETH_IPV4 + ip + udp);

        PacketMeta p = PacketParser.parse(frame, null, NOW).orElseThrow();

        assertThat(p.protocol()).isEqualTo(TrafficProtocol.UDP);
        assertThat(p.dstIp()).isEqualTo("224.0.0.251");
        assertThat(p.srcPort()).isEqualTo(5353);
        assertThat(p.dstPort()).isEqualTo(5353);
        assertThat(p.length()).isEqualTo(frame.length);
    }

    @Test
    void skipsVlanTag() {
        String eth = "ffeeddccbbaa" + "112233445566" + "8100" + "0064" + "0800";
        String ip = "45000014" + "00000000" + "4001" + "0000" + "c0a8640a" + "c0a86401";
        PacketMeta p = PacketParser.parse(HEX.parseHex(eth + ip), 60, NOW).orElseThrow();

        assertThat(p.protocol()).isEqualTo(TrafficProtocol.ICMP);
        assertThat(p.srcPort()).isNull();
        assertThat(p.dstIp()).isEqualTo("192.168.100.1");
    }

    @Test
    void parsesArpUsingSenderFields() {
        String eth = "ffffffffffff" + "112233445566" + "0806";
        String arp = "0001" + "0800" + "06" + "04" + "0001"
                + "aabbccddeeff" + "c0a8640a" // remetente
                + "000000000000" + "c0a86401"; // alvo
        PacketMeta p = PacketParser.parse(HEX.parseHex(eth + arp), 60, NOW).orElseThrow();

        assertThat(p.protocol()).isEqualTo(TrafficProtocol.ARP);
        assertThat(p.srcMac()).isEqualTo("aa:bb:cc:dd:ee:ff");
        assertThat(p.srcIp()).isEqualTo("192.168.100.10");
        assertThat(p.dstIp()).isEqualTo("192.168.100.1");
        assertThat(p.dstMac()).isEqualTo("ff:ff:ff:ff:ff:ff");
    }

    @Test
    void laterFragmentsHaveNoPorts() {
        // fragment offset != 0
        String ip = "45000034" + "00000010" + "4006" + "0000" + "c0a8640a" + "c0a86414";
        String data = "c35001bd00000000";
        PacketMeta p = PacketParser.parse(HEX.parseHex(ETH_IPV4 + ip + data), 66, NOW).orElseThrow();

        assertThat(p.protocol()).isEqualTo(TrafficProtocol.TCP);
        assertThat(p.srcPort()).isNull();
        assertThat(p.dstPort()).isNull();
    }

    @Test
    void ignoresIpv6TruncatedAndGarbage() {
        String ipv6 = "ffeeddccbbaa" + "112233445566" + "86dd" + "6000000000000000";
        assertThat(PacketParser.parse(HEX.parseHex(ipv6), 60, NOW)).isEmpty();
        assertThat(PacketParser.parse(HEX.parseHex(ETH_IPV4 + "4500"), 60, NOW)).isEmpty();
        assertThat(PacketParser.parse(new byte[5], 5, NOW)).isEmpty();
        assertThat(PacketParser.parse(null, null, NOW)).isEmpty();
    }
}
