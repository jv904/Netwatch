package com.netwatch.netwatch_api.ports;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.device.Device;
import com.netwatch.netwatch_api.device.DeviceRepository;
import com.netwatch.netwatch_api.ports.dto.NetworkPortResponse;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Consulta das portas de todos os dispositivos (tela "Portas e serviços"). */
@Service
@Transactional(readOnly = true)
public class PortInventoryService {

    private final DevicePortRepository devicePorts;
    private final DeviceRepository devices;

    public PortInventoryService(DevicePortRepository devicePorts, DeviceRepository devices) {
        this.devicePorts = devicePorts;
        this.devices = devices;
    }

    public Page<NetworkPortResponse> search(PortState state, Integer port, String search, Pageable pageable) {
        Specification<DevicePort> spec = (root, query, cb) -> {
            Root<Device> device = query.from(Device.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(device.get("id"), root.get("deviceId")));
            if (state != null) {
                predicates.add(cb.equal(root.get("state"), state));
            }
            if (port != null) {
                predicates.add(cb.equal(root.get("port"), port));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(device.get("ipAddress")), pattern, '\\'),
                        cb.like(cb.lower(device.get("customName")), pattern, '\\'),
                        cb.like(cb.lower(device.get("hostname")), pattern, '\\'),
                        cb.like(cb.lower(device.get("vendor")), pattern, '\\'),
                        cb.like(cb.lower(root.get("service")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<DevicePort> page = devicePorts.findAll(spec, pageable);
        List<UUID> ids = page.getContent().stream().map(DevicePort::getDeviceId).distinct().toList();
        Map<UUID, Device> byId = devices.findAllById(ids).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));
        return page.map(dp -> NetworkPortResponse.from(dp, byId.get(dp.getDeviceId())));
    }

    public List<DevicePortRepository.ExposedService> exposedServices() {
        return devicePorts.findExposedServices();
    }

    public long openPortCount() {
        return devicePorts.countByState(PortState.OPEN);
    }
}
