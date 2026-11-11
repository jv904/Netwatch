package com.netwatch.netwatch_api.traffic.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.discovery.Ipv4Network;

class BpfFilterBuilderTest {

    @Test
    void joinsAuthorizedNetworks() {
        String filter = BpfFilterBuilder.build(List.of(
                Ipv4Network.parse("192.168.100.0/24"),
                Ipv4Network.parse("10.0.5.0/24"),
                Ipv4Network.parse("192.168.100.7/24")));

        assertThat(filter).isEqualTo("net 192.168.100.0/24 or net 10.0.5.0/24");
    }

    @Test
    void refusesEmptyScope() {
        assertThatThrownBy(() -> BpfFilterBuilder.build(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
