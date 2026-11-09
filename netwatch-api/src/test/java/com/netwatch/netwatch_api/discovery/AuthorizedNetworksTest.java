package com.netwatch.netwatch_api.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces.LocalAddress;

class AuthorizedNetworksTest {

    static DiscoveryProperties props(List<String> networks) {
        return props(networks, false);
    }

    static DiscoveryProperties props(List<String> networks, boolean resolveHostnames) {
        return new DiscoveryProperties(networks, 1024, 8, Duration.ofMillis(100), List.of(80), resolveHostnames,
                new DiscoveryProperties.Schedule(false, Duration.ofMinutes(15)));
    }

    static LocalAddress nic(String name, String ip, int prefix, boolean virtual) {
        return new LocalAddress(name, name, ip, prefix, "aa:bb:cc:dd:ee:ff", virtual);
    }

    @Test
    void detectsLocalNetworksPreferringPhysicalInterfaces() {
        var networks = new AuthorizedNetworks(props(List.of()), () -> List.of(
                nic("vEthernet (WSL)", "172.25.160.1", 20, true),
                nic("Wi-Fi", "192.168.100.21", 24, false),
                nic("Public", "200.1.2.3", 24, false)));

        assertThat(networks.list()).extracting(a -> a.network().toString())
                .containsExactly("192.168.100.0/24", "172.25.160.0/24");
        assertThat(networks.resolve(null)).hasToString("192.168.100.0/24");
    }

    @Test
    void acceptsSubnetInsideAuthorizedNetwork() {
        var networks = new AuthorizedNetworks(props(List.of("192.168.0.0/22")), List::of);
        assertThat(networks.resolve("192.168.1.0/24")).hasToString("192.168.1.0/24");
    }

    @Test
    void rejectsOutOfScopePublicOrTooLarge() {
        var networks = new AuthorizedNetworks(props(List.of("192.168.0.0/16")), List::of);

        assertThatThrownBy(() -> networks.resolve("10.0.0.0/24"))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("não está dentro");
        assertThatThrownBy(() -> networks.resolve("8.8.8.0/24"))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("privadas");
        assertThatThrownBy(() -> networks.resolve("192.168.0.0/20"))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("máximo");
    }

    @Test
    void failsWhenNoNetworkAvailable() {
        var networks = new AuthorizedNetworks(props(List.of()), List::of);
        assertThatThrownBy(() -> networks.resolve(null)).isInstanceOf(InvalidInputException.class);
    }
}
