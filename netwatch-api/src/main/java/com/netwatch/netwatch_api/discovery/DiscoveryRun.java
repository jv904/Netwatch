package com.netwatch.netwatch_api.discovery;

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

/** Registro de uma execução da descoberta de dispositivos. */
@Entity
@Table(name = "discovery_runs")
public class DiscoveryRun {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 18)
    private String cidr;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DiscoveryRunStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "run_trigger", nullable = false, length = 20)
    private DiscoveryTrigger trigger;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "hosts_scanned", nullable = false)
    private int hostsScanned;

    @Column(name = "devices_found", nullable = false)
    private int devicesFound;

    @Column(name = "new_devices", nullable = false)
    private int newDevices;

    @Column(nullable = false)
    private int inactivated;

    @Column(name = "error_message", length = MAX_ERROR_LENGTH)
    private String errorMessage;

    protected DiscoveryRun() {
    }

    DiscoveryRun(String cidr, DiscoveryTrigger trigger, Instant startedAt) {
        this.cidr = cidr;
        this.trigger = trigger;
        this.startedAt = startedAt;
        this.status = DiscoveryRunStatus.RUNNING;
    }

    void complete(Instant at, int hostsScanned, int devicesFound, int newDevices, int inactivated) {
        this.status = DiscoveryRunStatus.COMPLETED;
        this.finishedAt = at;
        this.hostsScanned = hostsScanned;
        this.devicesFound = devicesFound;
        this.newDevices = newDevices;
        this.inactivated = inactivated;
    }

    void fail(Instant at, String message) {
        this.status = DiscoveryRunStatus.FAILED;
        this.finishedAt = at;
        this.errorMessage = message != null && message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH)
                : message;
    }

    public UUID getId() { return id; }
    public String getCidr() { return cidr; }
    public DiscoveryRunStatus getStatus() { return status; }
    public DiscoveryTrigger getTrigger() { return trigger; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getHostsScanned() { return hostsScanned; }
    public int getDevicesFound() { return devicesFound; }
    public int getNewDevices() { return newDevices; }
    public int getInactivated() { return inactivated; }
    public String getErrorMessage() { return errorMessage; }
}
