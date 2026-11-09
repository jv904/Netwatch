package com.netwatch.netwatch_api.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortScanRepository extends JpaRepository<PortScan, UUID> {

    Page<PortScan> findByDeviceId(UUID deviceId, Pageable pageable);

    List<PortScan> findByStatus(PortScanStatus status);

    Optional<PortScan> findFirstByDeviceIdOrderByStartedAtDesc(UUID deviceId);
}
