package com.netwatch.netwatch_api.ports;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.netwatch.netwatch_api.common.net.PortState;

public interface DevicePortRepository extends JpaRepository<DevicePort, DevicePort.Key>, JpaSpecificationExecutor<DevicePort> {

    List<DevicePort> findByDeviceIdOrderByPort(UUID deviceId);

    List<DevicePort> findByDeviceIdAndPortIn(UUID deviceId, List<Integer> ports);

    long countByState(PortState state);

    List<DevicePort> findByStateOrderByDeviceIdAscPortAsc(PortState state);

    /** Serviços mais expostos: portas abertas agrupadas, da mais frequente para a menos. */
    @Query("""
            select dp.port as port, max(dp.service) as service, count(dp) as devices
            from DevicePort dp
            where dp.state = com.netwatch.netwatch_api.common.net.PortState.OPEN
            group by dp.port
            order by count(dp) desc, dp.port asc
            """)
    List<ExposedService> findExposedServices();

    interface ExposedService {
        int getPort();
        String getService();
        long getDevices();
    }
}
