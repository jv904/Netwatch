package com.netwatch.netwatch_api.traffic;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrafficMinuteStatRepository extends JpaRepository<TrafficMinuteStat, UUID> {

    Optional<TrafficMinuteStat> findByBucketStartAndProtocol(Instant bucketStart, TrafficProtocol protocol);

    List<TrafficMinuteStat> findByBucketStartGreaterThanEqualOrderByBucketStart(Instant since);

    @Modifying
    @Query("delete from TrafficMinuteStat s where s.bucketStart < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
