package com.netwatch.netwatch_api.ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

class PortSpecTest {

    @Test
    void parsesListsAndRangesSortedWithoutDuplicates() {
        assertThat(PortSpec.parse(" 80, 22,8000-8003, 22 ", 100))
                .containsExactly(22, 80, 8000, 8001, 8002, 8003);
    }

    @Test
    void enforcesMaximum() {
        assertThat(PortSpec.parse("1-1024", 1024)).hasSize(1024);
        assertThatThrownBy(() -> PortSpec.parse("1-1025", 1024))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("1024");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0", "65536", "100-50", "22,,x", "-"})
    void rejectsInvalidSpecs(String spec) {
        assertThatThrownBy(() -> PortSpec.parse(spec, 1024)).isInstanceOf(InvalidInputException.class);
    }
}
