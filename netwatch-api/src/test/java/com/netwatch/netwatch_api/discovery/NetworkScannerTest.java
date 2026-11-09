package com.netwatch.netwatch_api.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces.LocalAddress;
import com.netwatch.netwatch_api.discovery.vendor.MacVendorLookup;

class NetworkScannerTest {

    @Test
    void mergesProbeArpAndLocalMachine() throws InterruptedException {
        Set<String> alive = Set.of("192.168.1.1", "192.168.1.30");
        Map<String, String> arp = Map.of(
                "192.168.1.1", "3c:5a:b4:00:00:01",   // respondeu e está no ARP
                "192.168.1.40", "da:a1:19:00:00:02",  // bloqueia ping/TCP, mas está no ARP
                "10.9.9.9", "aa:aa:aa:aa:aa:aa");     // fora da faixa: ignorado

        NetworkScanner scanner = new NetworkScanner(
                alive::contains,
                () -> arp,
                ip -> ip.equals("192.168.1.1") ? Optional.of("router.lan") : Optional.empty(),
                () -> List.of(new LocalAddress("eth0", "eth0", "192.168.1.10", 24, "00:11:22:33:44:55", false)),
                new MacVendorLookup(),
                AuthorizedNetworksTest.props(List.of(), true));

        NetworkScanner.ScanResult result = scanner.scan(Ipv4Network.parse("192.168.1.0/24"));

        assertThat(result.hostsScanned()).isEqualTo(254);
        assertThat(result.observations()).extracting(DeviceObservation::ipAddress)
                .containsExactly("192.168.1.1", "192.168.1.10", "192.168.1.30", "192.168.1.40");

        DeviceObservation router = result.observations().getFirst();
        assertThat(router.macAddress()).isEqualTo("3c:5a:b4:00:00:01");
        assertThat(router.hostname()).isEqualTo("router.lan");
        assertThat(router.vendor()).isEqualTo("Google, Inc.");

        DeviceObservation self = result.observations().get(1);
        assertThat(self.macAddress()).isEqualTo("00:11:22:33:44:55");

        DeviceObservation noMac = result.observations().get(2);
        assertThat(noMac.macAddress()).isNull();

        DeviceObservation silent = result.observations().get(3);
        assertThat(silent.vendor()).isEqualTo(MacVendorLookup.RANDOMIZED);
    }
}
