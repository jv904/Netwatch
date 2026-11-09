package com.netwatch.netwatch_api.device;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Equipamento identificado (ou cadastrado manualmente) na rede monitorada.
 */
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "mac_address", unique = true, length = 17)
    private String macAddress;

    @Column(length = 255)
    private String hostname;

    @Column(length = 255)
    private String vendor;

    /** Nome dado pelo usuário (ex.: "Notebook da Ana"). */
    @Column(name = "custom_name", length = 100)
    private String customName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "device_type", nullable = false, length = 30)
    private DeviceType type = DeviceType.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.INACTIVE;

    /** Se o usuário já reconheceu este equipamento. Não reconhecido = "desconhecido". */
    @Column(nullable = false)
    private boolean recognized;

    @Column(length = 1000)
    private String notes;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Device() {
    }

    public Device(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Registra que o equipamento foi visto na rede no instante informado. */
    void markSeen(Instant at) {
        if (firstSeenAt == null) {
            firstSeenAt = at;
        }
        lastSeenAt = at;
        status = DeviceStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public String getIpAddress() { return ipAddress; }
    public String getMacAddress() { return macAddress; }
    public String getHostname() { return hostname; }
    public String getVendor() { return vendor; }
    public String getCustomName() { return customName; }
    public DeviceType getType() { return type; }
    public DeviceStatus getStatus() { return status; }
    public boolean isRecognized() { return recognized; }
    public String getNotes() { return notes; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    void setHostname(String hostname) { this.hostname = hostname; }
    void setVendor(String vendor) { this.vendor = vendor; }
    void setCustomName(String customName) { this.customName = customName; }
    void setType(DeviceType type) { this.type = type; }
    void setStatus(DeviceStatus status) { this.status = status; }
    void setRecognized(boolean recognized) { this.recognized = recognized; }
    void setNotes(String notes) { this.notes = notes; }
}
