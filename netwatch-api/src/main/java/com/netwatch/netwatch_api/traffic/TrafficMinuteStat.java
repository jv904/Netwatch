package com.netwatch.netwatch_api.traffic;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Total de pacotes e bytes de um protocolo em um minuto. */
@Entity
@Table(name = "traffic_stats_minute")
public class TrafficMinuteStat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private TrafficProtocol protocol;

    @Column(nullable = false)
    private long packets;

    @Column(nullable = false)
    private long bytes;

    /** Maior número de conexões simultâneas visto no minuto (modo tabela de conexões). */
    @Column(nullable = false)
    private long connections;

    protected TrafficMinuteStat() {
    }

    TrafficMinuteStat(Instant bucketStart, TrafficProtocol protocol) {
        this.bucketStart = bucketStart;
        this.protocol = protocol;
    }

    void add(long packets, long bytes) {
        this.packets += packets;
        this.bytes += bytes;
    }

    void observeConnections(long concurrent) {
        this.connections = Math.max(this.connections, concurrent);
    }

    public long getConnections() { return connections; }

    public UUID getId() { return id; }
    public Instant getBucketStart() { return bucketStart; }
    public TrafficProtocol getProtocol() { return protocol; }
    public long getPackets() { return packets; }
    public long getBytes() { return bytes; }
}
