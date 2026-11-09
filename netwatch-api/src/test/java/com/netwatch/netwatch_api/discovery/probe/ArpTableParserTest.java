package com.netwatch.netwatch_api.discovery.probe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArpTableParserTest {

    @Test
    void parsesWindowsPortuguese() {
        String output = """

                Interface: 192.168.100.21 --- 0x6
                  Endereço IP           Endereço físico       Tipo
                  192.168.100.1         48-8f-5a-ae-4f-06     dinâmico
                  192.168.100.15        00-E0-4C-68-02-6A     dinâmico
                  192.168.100.255       ff-ff-ff-ff-ff-ff     estático
                  224.0.0.22            01-00-5e-00-00-16     estático
                  239.255.255.250       01-00-5e-7f-ff-fa     estático
                """;

        assertThat(ArpTableParser.parse(output))
                .containsOnlyKeys("192.168.100.1", "192.168.100.15")
                .containsEntry("192.168.100.1", "48:8f:5a:ae:4f:06")
                .containsEntry("192.168.100.15", "00:e0:4c:68:02:6a");
    }

    @Test
    void parsesWindowsEnglish() {
        String output = """
                Interface: 10.0.0.5 --- 0xb
                  Internet Address      Physical Address      Type
                  10.0.0.1              aa-bb-cc-dd-ee-ff     dynamic
                """;
        assertThat(ArpTableParser.parse(output)).containsExactlyEntriesOf(
                java.util.Map.of("10.0.0.1", "aa:bb:cc:dd:ee:ff"));
    }

    @Test
    void parsesLinuxProcNetArpAndSkipsIncomplete() {
        String output = """
                IP address       HW type     Flags       HW address            Mask     Device
                192.168.1.1      0x1         0x2         48:8f:5a:ae:4f:06     *        eth0
                192.168.1.77     0x1         0x0         00:00:00:00:00:00     *        eth0
                """;
        assertThat(ArpTableParser.parse(output))
                .containsOnlyKeys("192.168.1.1");
    }

    @Test
    void parsesMacOsWithSingleDigitOctets() {
        String output = """
                ? (192.168.1.1) at 48:8f:5a:ae:4f:6 on en0 ifscope [ethernet]
                ? (192.168.1.9) at (incomplete) on en0 ifscope [ethernet]
                """;
        assertThat(ArpTableParser.parse(output))
                .containsOnlyKeys("192.168.1.1")
                .containsEntry("192.168.1.1", "48:8f:5a:ae:4f:06");
    }

    @Test
    void handlesEmptyOutput() {
        assertThat(ArpTableParser.parse(null)).isEmpty();
        assertThat(ArpTableParser.parse("")).isEmpty();
    }
}
