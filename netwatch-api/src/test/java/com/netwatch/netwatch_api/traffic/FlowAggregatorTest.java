package com.netwatch.netwatch_api.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.traffic.FlowAggregator.Drain;
import com.netwatch.netwatch_api.traffic.FlowAggregator.FlowDelta;
import com.netwatch.netwatch_api.traffic.capture.PacketMeta;

class FlowAggregatorTest {

    static final Instant T0 = Instant.parse("2026-11-11T12:00:30Z");

    static PacketMeta tcp(Instant at, String src, int sport, String dst, int dport, int length, int flags) {
        return new PacketMeta(at, "aa:00:00:00:00:01", "aa:00:00:00:00:02", src, dst, TrafficProtocol.TCP, sport,
                dport, length, flags);
    }

    @Test
    void groupsPacketsByFiveTuple() {
        FlowAggregator aggregator = new FlowAggregator();
        aggregator.add(tcp(T0, "192.168.1.10", 50000, "192.168.1.20", 445, 60, 0x02));
        aggregator.add(tcp(T0.plusSeconds(1), "192.168.1.10", 50000, "192.168.1.20", 445, 1500, 0x10));
        aggregator.add(tcp(T0.plusSeconds(2), "192.168.1.20", 445, "192.168.1.10", 50000, 100, 0x12));

        Drain drain = aggregator.drain();

        assertThat(drain.flows()).hasSize(2);
        FlowDelta outbound = drain.flows().stream().filter(f -> f.key().dstPort() == 445).findFirst().orElseThrow();
        assertThat(outbound.packets()).isEqualTo(2);
        assertThat(outbound.bytes()).isEqualTo(1560);
        assertThat(outbound.tcpFlags()).isEqualTo(0x12);
        assertThat(outbound.firstSeen()).isEqualTo(T0);
        assertThat(outbound.lastSeen()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void bucketsStatsPerMinuteAndProtocol() {
        FlowAggregator aggregator = new FlowAggregator();
        aggregator.add(tcp(T0, "192.168.1.10", 1, "192.168.1.20", 2, 100, 0));
        aggregator.add(tcp(T0.plusSeconds(45), "192.168.1.10", 1, "192.168.1.20", 2, 200, 0));

        Drain drain = aggregator.drain();

        assertThat(drain.stats()).hasSize(2);
        assertThat(drain.stats()).extracting(FlowAggregator.StatDelta::bucket)
                .containsExactlyInAnyOrder(Instant.parse("2026-11-11T12:00:00Z"), Instant.parse("2026-11-11T12:01:00Z"));
    }

    @Test
    void drainResetsCounters() {
        FlowAggregator aggregator = new FlowAggregator();
        aggregator.add(tcp(T0, "192.168.1.10", 1, "192.168.1.20", 2, 100, 0));
        aggregator.drain();

        assertThat(aggregator.drain().isEmpty()).isTrue();
    }
}
