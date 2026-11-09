package com.netwatch.netwatch_api.ports;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortScanResultRepository extends JpaRepository<PortScanResult, UUID> {

    List<PortScanResult> findByScanIdOrderByPort(UUID scanId);
}
