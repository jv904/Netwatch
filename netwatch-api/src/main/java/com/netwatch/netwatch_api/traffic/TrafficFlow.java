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

/** Pacotes com o mesmo protocolo, origem e destino observados em sequência. */
@Entity
@Table(name = "traffic_flows")
public class TrafficFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private TrafficProtocol protocol;

    @Column(name = "src_ip", nullable = false, length = 45)
    private String srcIp;

    @Column(name = "src_port")
    private Integer srcPort;

    @Column(name = "dst_ip", nullable = false, length = 45)
    private String dstIp;

    @Column(name = "dst_port")
    private Integer dstPort;

    /** Porta do lado "servidor" (a menor das duas), usada para identificar o serviço. */
    @Column(name = "service_port")
    private Integer servicePort;

    @Column(name = "src_mac", length = 17)
    private String srcMac;

    @Column(name = "dst_mac", length = 17)
    private String dstMac;

    @Column(name = "src_device_id")
    private UUID srcDeviceId;

    @Column(name = "dst_device_id")
    private UUID dstDeviceId;

    /** Executável dono da conexão (só no modo tabela de conexões). */
    @Column(name = "process_name")
    private String processName;

    /** Último estado TCP visto (só no modo tabela de conexões). */
    @Column(name = "connection_state", length = 20)
    private String connectionState;

    @Column(nullable = false)
    private long packets;

    @Column(nullable = false)
    private long bytes;

    @Column(name = "tcp_flags", nullable = false)
    private int tcpFlags;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected TrafficFlow() {
    }

    TrafficFlow(UUID sessionId, TrafficProtocol protocol, String srcIp, Integer srcPort, String dstIp,
            Integer dstPort, Instant firstSeen) {
        this.sessionId = sessionId;
        this.protocol = protocol;
        this.srcIp = srcIp;
        this.srcPort = srcPort;
        this.dstIp = dstIp;
        this.dstPort = dstPort;
        this.servicePort = srcPort != null && dstPort != null ? Math.min(srcPort, dstPort) : null;
        this.firstSeen = firstSeen;
        this.lastSeen = firstSeen;
    }

    void add(long packets, long bytes, int tcpFlags, Instant lastSeen, String srcMac, String dstMac) {
        this.packets += packets;
        this.bytes += bytes;
        this.tcpFlags |= tcpFlags;
        if (lastSeen.isAfter(this.lastSeen)) {
            this.lastSeen = lastSeen;
        }
        if (srcMac != null) {
            this.srcMac = srcMac;
        }
        if (dstMac != null) {
            this.dstMac = dstMac;
        }
    }

    void describeConnection(String processName, String connectionState) {
        if (processName != null) {
            this.processName = processName.length() > 255 ? processName.substring(0, 255) : processName;
        }
        if (connectionState != null) {
            this.connectionState = connectionState.length() > 20 ? connectionState.substring(0, 20) : connectionState;
        }
    }

    void linkDevices(UUID srcDeviceId, UUID dstDeviceId) {
        if (srcDeviceId != null) {
            this.srcDeviceId = srcDeviceId;
        }
        if (dstDeviceId != null) {
            this.dstDeviceId = dstDeviceId;
        }
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public TrafficProtocol getProtocol() { return protocol; }
    public String getSrcIp() { return srcIp; }
    public Integer getSrcPort() { return srcPort; }
    public String getDstIp() { return dstIp; }
    public Integer getDstPort() { return dstPort; }
    public Integer getServicePort() { return servicePort; }
    public String getSrcMac() { return srcMac; }
    public String getDstMac() { return dstMac; }
    public UUID getSrcDeviceId() { return srcDeviceId; }
    public UUID getDstDeviceId() { return dstDeviceId; }
    public String getProcessName() { return processName; }
    public String getConnectionState() { return connectionState; }
    public long getPackets() { return packets; }
    public long getBytes() { return bytes; }
    public int getTcpFlags() { return tcpFlags; }
    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
}
