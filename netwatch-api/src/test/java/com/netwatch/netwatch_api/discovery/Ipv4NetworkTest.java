package com.netwatch.netwatch_api.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

class Ipv4NetworkTest {

    @Test
    void parsesAndNormalizesNetworkAddress() {
        Ipv4Network network = Ipv4Network.parse("192.168.100.21/24");
        assertThat(network).hasToString("192.168.100.0/24");
        assertThat(network.hostCount()).isEqualTo(254);
    }

    @Test
    void listsHostsWithoutNetworkAndBroadcast() {
        var hosts = Ipv4Network.parse("10.0.0.0/30").hosts();
        assertThat(hosts).containsExactly("10.0.0.1", "10.0.0.2");
    }

    @Test
    void containsAddressesAndSubnets() {
        Ipv4Network network = Ipv4Network.parse("192.168.0.0/16");
        assertThat(network.contains("192.168.10.5")).isTrue();
        assertThat(network.contains("192.169.0.1")).isFalse();
        assertThat(network.contains("not-an-ip")).isFalse();
        assertThat(network.contains(Ipv4Network.parse("192.168.1.0/24"))).isTrue();
        assertThat(Ipv4Network.parse("192.168.1.0/24").contains(network)).isFalse();
    }

    @Test
    void detectsPrivateRanges() {
        assertThat(Ipv4Network.parse("10.1.0.0/16").isPrivate()).isTrue();
        assertThat(Ipv4Network.parse("172.20.0.0/24").isPrivate()).isTrue();
        assertThat(Ipv4Network.parse("192.168.1.0/24").isPrivate()).isTrue();
        assertThat(Ipv4Network.parse("8.8.8.0/24").isPrivate()).isFalse();
        assertThat(Ipv4Network.parse("172.32.0.0/24").isPrivate()).isFalse();
        assertThat(Ipv4Network.parse("192.0.0.0/2").isPrivate()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.0", "192.168.0.0/33", "300.1.1.1/24", "abc/24", ""})
    void rejectsInvalidCidr(String cidr) {
        assertThatThrownBy(() -> Ipv4Network.parse(cidr)).isInstanceOf(InvalidInputException.class);
    }
}
