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

/** Um período de captura de tráfego em uma interface de rede. */
@Entity
@Table(name = "capture_sessions")
public class CaptureSession {

    private static final int MAX_TEXT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CaptureMode mode;

    @Column(name = "interface_name", nullable = false)
    private String interfaceName;

    @Column(name = "interface_description")
    private String interfaceDescription;

    /** IP desta máquina na interface capturada. */
    @Column(name = "local_address", length = 45)
    private String localAddress;

    @Column(name = "bpf_filter", nullable = false, length = MAX_TEXT)
    private String bpfFilter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private CaptureSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "planned_end_at", nullable = false)
    private Instant plannedEndAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private long packets;

    @Column(nullable = false)
    private long bytes;

    @Column(name = "dropped_packets", nullable = false)
    private long droppedPackets;

    @Column(name = "new_devices", nullable = false)
    private int newDevices;

    @Column(name = "error_message", length = MAX_TEXT)
    private String errorMessage;

    protected CaptureSession() {
    }

    CaptureSession(CaptureMode mode, String interfaceName, String interfaceDescription, String localAddress,
            String bpfFilter, Instant startedAt, Instant plannedEndAt) {
        this.mode = mode;
        this.interfaceName = interfaceName;
        this.interfaceDescription = interfaceDescription;
        this.localAddress = localAddress;
        this.bpfFilter = truncate(bpfFilter);
        this.startedAt = startedAt;
        this.plannedEndAt = plannedEndAt;
        this.status = CaptureSessionStatus.RUNNING;
    }

    void updateCounters(long packets, long bytes, int newDevices) {
        this.packets = packets;
        this.bytes = bytes;
        this.newDevices = newDevices;
    }

    void finish(CaptureSessionStatus status, Instant at, long dropped, String error) {
        this.status = status;
        this.finishedAt = at;
        this.droppedPackets = dropped;
        this.errorMessage = truncate(error);
    }

    private static String truncate(String value) {
        return value != null && value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }

    public UUID getId() { return id; }
    public CaptureMode getMode() { return mode; }
    public String getInterfaceName() { return interfaceName; }
    public String getInterfaceDescription() { return interfaceDescription; }
    public String getLocalAddress() { return localAddress; }
    public String getBpfFilter() { return bpfFilter; }
    public CaptureSessionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getPlannedEndAt() { return plannedEndAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public long getPackets() { return packets; }
    public long getBytes() { return bytes; }
    public long getDroppedPackets() { return droppedPackets; }
    public int getNewDevices() { return newDevices; }
    public String getErrorMessage() { return errorMessage; }
}
