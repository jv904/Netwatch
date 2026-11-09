package com.netwatch.netwatch_api.device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {

    Optional<Device> findByMacAddress(String macAddress);

    boolean existsByMacAddress(String macAddress);

    /** Equipamento sem MAC conhecido com este IP (ex.: cadastrado manualmente só com IP). */
    Optional<Device> findFirstByIpAddressAndMacAddressIsNullOrderByLastSeenAtDesc(String ipAddress);

    boolean existsByIpAddressAndMacAddressIsNull(String ipAddress);

    long countByStatus(DeviceStatus status);

    long countByRecognizedFalse();

    /** Quantidade de dispositivos por fabricante (sem fabricante identificado = nulo). */
    @Query("""
            select d.vendor as vendor, count(d) as total
            from Device d
            group by d.vendor
            order by count(d) desc
            """)
    List<VendorCount> countByVendor(Limit limit);

    interface VendorCount {
        String getVendor();
        long getTotal();
    }
}
