package com.netwatch.netwatch_api.ports;

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

/** Uma verificação de portas em um dispositivo. */
@Entity
@Table(name = "port_scans")
public class PortScan {

    private static final int MAX_TEXT = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    /** IP no momento da verificação (o IP do dispositivo pode mudar depois). */
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scan_type", nullable = false, length = 20)
    private PortScanType type;

    @Column(name = "requested_ports", nullable = false, length = 2000)
    private String requestedPorts;

    @Column(name = "port_count", nullable = false)
    private int portCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PortScanStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "open_count", nullable = false)
    private int openCount;

    @Column(name = "closed_count", nullable = false)
    private int closedCount;

    @Column(name = "filtered_count", nullable = false)
    private int filteredCount;

    @Column(name = "error_message", length = MAX_TEXT)
    private String errorMessage;

    protected PortScan() {
    }

    PortScan(UUID deviceId, String ipAddress, PortScanType type, String requestedPorts, int portCount, Instant startedAt) {
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        this.type = type;
        this.requestedPorts = requestedPorts.length() > 2000 ? requestedPorts.substring(0, 2000) : requestedPorts;
        this.portCount = portCount;
        this.startedAt = startedAt;
        this.status = PortScanStatus.RUNNING;
    }

    void complete(Instant at, int open, int closed, int filtered) {
        status = PortScanStatus.COMPLETED;
        finishedAt = at;
        openCount = open;
        closedCount = closed;
        filteredCount = filtered;
    }

    void fail(Instant at, String message) {
        status = PortScanStatus.FAILED;
        finishedAt = at;
        errorMessage = message != null && message.length() > MAX_TEXT ? message.substring(0, MAX_TEXT) : message;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public String getIpAddress() { return ipAddress; }
    public PortScanType getType() { return type; }
    public String getRequestedPorts() { return requestedPorts; }
    public int getPortCount() { return portCount; }
    public PortScanStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getOpenCount() { return openCount; }
    public int getClosedCount() { return closedCount; }
    public int getFilteredCount() { return filteredCount; }
    public String getErrorMessage() { return errorMessage; }
}
