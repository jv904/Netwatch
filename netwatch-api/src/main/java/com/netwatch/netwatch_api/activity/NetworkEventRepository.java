package com.netwatch.netwatch_api.activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NetworkEventRepository extends JpaRepository<NetworkEvent, UUID>,
        JpaSpecificationExecutor<NetworkEvent> {

    List<NetworkEvent> findByOccurredAtGreaterThanEqual(Instant since);

    /** Eventos do período [from, to). */
    List<NetworkEvent> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAt(Instant from, Instant to);

    long countByType(EventType type);

    @Modifying
    @Query("delete from NetworkEvent e where e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
