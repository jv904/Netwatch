package com.netwatch.netwatch_api.ports;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.netwatch.netwatch_api.common.net.PortState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Resultado de uma porta em uma verificação (histórico). */
@Entity
@Table(name = "port_scan_results")
public class PortScanResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PortState state;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "previous_state", length = 20)
    private PortState previousState;

    /** A porta estava fechada/sem resposta na verificação anterior e agora aceita conexões. */
    @Column(name = "newly_opened", nullable = false)
    private boolean newlyOpened;

    @Column(length = 100)
    private String service;

    protected PortScanResult() {
    }

    PortScanResult(UUID scanId, int port, PortState state, PortState previousState, String service) {
        this.scanId = scanId;
        this.port = port;
        this.state = state;
        this.previousState = previousState;
        this.service = service;
        this.newlyOpened = state == PortState.OPEN && previousState != null && previousState != PortState.OPEN;
    }

    public UUID getId() { return id; }
    public UUID getScanId() { return scanId; }
    public int getPort() { return port; }
    public PortState getState() { return state; }
    public PortState getPreviousState() { return previousState; }
    public boolean isNewlyOpened() { return newlyOpened; }
    public String getService() { return service; }
}
