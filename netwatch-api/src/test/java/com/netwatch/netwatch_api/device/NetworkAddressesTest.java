package com.netwatch.netwatch_api.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

class NetworkAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {"192.168.0.10", "10.0.0.1", "255.255.255.255", "0.0.0.0"})
    void acceptsValidIpv4(String ip) {
        assertThat(NetworkAddresses.normalizeIp(ip)).isEqualTo(ip);
    }

    @Test
    void normalizesIpv6() {
        assertThat(NetworkAddresses.normalizeIp("FE80::1")).isEqualTo("fe80:0:0:0:0:0:0:1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"256.1.1.1", "192.168.0", "192.168.0.01", "example.com", "abc", " "})
    void rejectsInvalidIp(String ip) {
        assertThatThrownBy(() -> NetworkAddresses.normalizeIp(ip)).isInstanceOf(InvalidInputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AA:BB:CC:DD:EE:FF", "aa-bb-cc-dd-ee-ff", "aabb.ccdd.eeff", "aabbccddeeff"})
    void normalizesMacFormats(String mac) {
        assertThat(NetworkAddresses.normalizeMac(mac)).isEqualTo("aa:bb:cc:dd:ee:ff");
    }

    @Test
    void blankMacIsNull() {
        assertThat(NetworkAddresses.normalizeMac("  ")).isNull();
        assertThat(NetworkAddresses.normalizeMac(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aa:bb:cc:dd:ee", "gg:bb:cc:dd:ee:ff", "aa:bb:cc:dd:ee:ff:00"})
    void rejectsInvalidMac(String mac) {
        assertThatThrownBy(() -> NetworkAddresses.normalizeMac(mac)).isInstanceOf(InvalidInputException.class);
    }
}
