package com.netwatch.netwatch_api.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.device.dto.CreateDeviceRequest;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceServiceTest {

    @Autowired
    DeviceService service;

    @Autowired
    DeviceRepository repository;

    @Test
    void newObservationCreatesUnrecognizedActiveDevice() {
        ObservationResult result = service.registerObservation(
                new DeviceObservation("192.168.0.25", "AA-BB-CC-00-11-22", "android-xyz", "Samsung"));

        assertThat(result.created()).isTrue();
        Device device = result.device();
        assertThat(device.isRecognized()).isFalse();
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(device.getMacAddress()).isEqualTo("aa:bb:cc:00:11:22");
        assertThat(device.getFirstSeenAt()).isNotNull().isEqualTo(device.getLastSeenAt());
    }

    @Test
    void sameMacWithNewIpUpdatesExistingDevice() {
        service.registerObservation(new DeviceObservation("192.168.0.10", "aa:bb:cc:00:11:22", "notebook", null));

        ObservationResult second = service.registerObservation(
                new DeviceObservation("192.168.0.42", "aa:bb:cc:00:11:22", null, null));

        assertThat(second.created()).isFalse();
        assertThat(second.ipChanged()).isTrue();
        assertThat(second.device().getIpAddress()).isEqualTo("192.168.0.42");
        assertThat(second.device().getHostname()).as("hostname vazio não apaga o anterior").isEqualTo("notebook");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void observationAdoptsManualDeviceRegisteredWithoutMac() {
        Device manual = service.create(new CreateDeviceRequest("192.168.0.20", null, null, "Impressora", DeviceType.PRINTER, null));
        assertThat(manual.getStatus()).isEqualTo(DeviceStatus.INACTIVE);
        assertThat(manual.isRecognized()).isTrue();

        ObservationResult result = service.registerObservation(
                new DeviceObservation("192.168.0.20", "11:22:33:44:55:66", null, "HP"));

        assertThat(result.created()).isFalse();
        assertThat(result.device().getId()).isEqualTo(manual.getId());
        assertThat(result.device().getMacAddress()).isEqualTo("11:22:33:44:55:66");
        assertThat(result.device().isRecognized()).isTrue();
        assertThat(result.device().getStatus()).isEqualTo(DeviceStatus.ACTIVE);
    }

    @Test
    void marksDevicesNotSeenRecentlyAsInactive() {
        service.registerObservation(new DeviceObservation("192.168.0.30", "aa:aa:aa:aa:aa:aa", null, null));
        service.registerObservation(new DeviceObservation("10.0.0.5", "aa:aa:aa:aa:aa:bb", null, null));

        int updated = service.markInactiveNotSeenSince(Instant.now().plusSeconds(60), ip -> ip.startsWith("192.168."));

        assertThat(updated).as("apenas o dispositivo dentro do escopo").isEqualTo(1);
        assertThat(service.summary().inactive()).isEqualTo(1);
        assertThat(service.summary().active()).isEqualTo(1);
        assertThat(service.summary().unrecognized()).isEqualTo(2);
    }
}
