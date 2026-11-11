package com.netwatch.netwatch_api.traffic;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrafficFlowRepository extends JpaRepository<TrafficFlow, UUID>, JpaSpecificationExecutor<TrafficFlow> {

    long countByLastSeenGreaterThanEqual(Instant since);

    boolean existsByDstDeviceIdAndProtocolAndServicePort(UUID dstDeviceId, TrafficProtocol protocol, Integer servicePort);

    /** Volume enviado por IP de origem. */
    @Query("""
            select f.srcIp as ip, sum(f.bytes) as bytes, sum(f.packets) as packets, count(f) as flows
            from TrafficFlow f
            where f.lastSeen >= :since
            group by f.srcIp
            """)
    List<IpTotal> totalsBySource(@Param("since") Instant since);

    /** Volume recebido por IP de destino. */
    @Query("""
            select f.dstIp as ip, sum(f.bytes) as bytes, sum(f.packets) as packets, count(f) as flows
            from TrafficFlow f
            where f.lastSeen >= :since
            group by f.dstIp
            """)
    List<IpTotal> totalsByDestination(@Param("since") Instant since);

    @Query("""
            select f.protocol as protocol, f.servicePort as port, sum(f.bytes) as bytes,
                   sum(f.packets) as packets, count(f) as flows
            from TrafficFlow f
            where f.lastSeen >= :since and f.servicePort is not null
            group by f.protocol, f.servicePort
            order by sum(f.bytes) desc, count(f) desc
            """)
    List<ServiceTotal> topServices(@Param("since") Instant since, Limit limit);

    /** O que o dispositivo enviou, por destino. */
    @Query("""
            select f.dstIp as ip, sum(f.bytes) as bytes, sum(f.packets) as packets, count(f) as flows
            from TrafficFlow f
            where f.srcDeviceId = :deviceId and f.lastSeen >= :since
            group by f.dstIp
            """)
    List<IpTotal> sentByDevice(@Param("deviceId") UUID deviceId, @Param("since") Instant since);

    /** O que o dispositivo recebeu, por origem. */
    @Query("""
            select f.srcIp as ip, sum(f.bytes) as bytes, sum(f.packets) as packets, count(f) as flows
            from TrafficFlow f
            where f.dstDeviceId = :deviceId and f.lastSeen >= :since
            group by f.srcIp
            """)
    List<IpTotal> receivedByDevice(@Param("deviceId") UUID deviceId, @Param("since") Instant since);

    @Modifying
    @Query("delete from TrafficFlow f where f.lastSeen < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    interface IpTotal {
        String getIp();
        long getBytes();
        long getPackets();
        long getFlows();
    }

    interface ServiceTotal {
        TrafficProtocol getProtocol();
        Integer getPort();
        long getBytes();
        long getPackets();
        long getFlows();
    }
}
