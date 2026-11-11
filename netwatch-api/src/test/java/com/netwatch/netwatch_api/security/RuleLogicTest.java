package com.netwatch.netwatch_api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

class RuleLogicTest {

    @Test
    void offHoursCrossingMidnight() {
        assertThat(SecurityEngine.isOffHours(23, 22, 6)).isTrue();
        assertThat(SecurityEngine.isOffHours(3, 22, 6)).isTrue();
        assertThat(SecurityEngine.isOffHours(6, 22, 6)).isFalse();
        assertThat(SecurityEngine.isOffHours(14, 22, 6)).isFalse();
    }

    @Test
    void offHoursWithinTheSameDay() {
        assertThat(SecurityEngine.isOffHours(13, 12, 14)).isTrue();
        assertThat(SecurityEngine.isOffHours(14, 12, 14)).isFalse();
        assertThat(SecurityEngine.isOffHours(10, 8, 8)).isFalse();
    }

    @Test
    void criticalIpsUseGatewayHeuristicWhenNotConfigured() {
        assertThat(SecurityEngine.isCriticalIp("192.168.0.1", List.of())).isTrue();
        assertThat(SecurityEngine.isCriticalIp("10.0.0.254", List.of())).isTrue();
        assertThat(SecurityEngine.isCriticalIp("192.168.0.11", List.of())).isFalse();
        assertThat(SecurityEngine.isCriticalIp("192.168.0.1", List.of("192.168.0.10"))).isFalse();
        assertThat(SecurityEngine.isCriticalIp("192.168.0.10", List.of("192.168.0.10"))).isTrue();
    }

    @Test
    void paramsAreValidatedAndNormalized() {
        RuleParam ports = RuleType.RISKY_SERVICE.param("ports");
        assertThat(ports.normalize(" 445, 22 ;22 3389")).isEqualTo("22,445,3389");
        assertThatThrownBy(() -> ports.normalize("80,70000")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ports.normalize("")).isInstanceOf(InvalidInputException.class);

        RuleParam hour = RuleType.OFF_HOURS.param("start");
        assertThat(hour.normalize("23")).isEqualTo("23");
        assertThatThrownBy(() -> hour.normalize("24")).isInstanceOf(InvalidInputException.class);

        RuleParam ips = RuleType.IP_CONFLICT.param("criticalIps");
        assertThat(ips.normalize("")).isEmpty();
        assertThat(ips.normalize("192.168.0.1, 192.168.0.1")).isEqualTo("192.168.0.1");
        assertThatThrownBy(() -> ips.normalize("roteador")).isInstanceOf(InvalidInputException.class);
    }
}
