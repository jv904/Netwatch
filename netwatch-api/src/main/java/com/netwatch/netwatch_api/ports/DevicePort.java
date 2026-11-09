package com.netwatch.netwatch_api.ports;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.netwatch.netwatch_api.common.net.PortState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Situação atual de uma porta de um dispositivo (última verificação). */
@Entity
@Table(name = "device_ports")
@IdClass(DevicePort.Key.class)
public class DevicePort {

    public static class Key implements Serializable {
        private UUID deviceId;
        private int port;

        protected Key() {
        }

        public Key(UUID deviceId, int port) {
            this.deviceId = deviceId;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && port == k.port && Objects.equals(deviceId, k.deviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceId, port);
        }
    }

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Id
    @Column(name = "port")
    private int port;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PortState state;

    @Column(length = 100)
    private String service;

    /** Desde quando a porta está aberta (nulo se não estiver). */
    @Column(name = "open_since")
    private Instant openSince;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    @Column(name = "last_state_change_at", nullable = false)
    private Instant lastStateChangeAt;

    protected DevicePort() {
    }

    DevicePort(UUID deviceId, int port) {
        this.deviceId = deviceId;
        this.port = port;
    }

    /** Aplica o resultado de uma verificação. */
    void applyResult(PortState newState, String serviceName, Instant at) {
        if (state != newState) {
            lastStateChangeAt = at;
            openSince = newState == PortState.OPEN ? at : null;
        }
        state = newState;
        service = serviceName;
        lastCheckedAt = at;
    }

    public UUID getDeviceId() { return deviceId; }
    public int getPort() { return port; }
    public PortState getState() { return state; }
    public String getService() { return service; }
    public Instant getOpenSince() { return openSince; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Instant getLastStateChangeAt() { return lastStateChangeAt; }
}
