CREATE TABLE port_scans (
    id              UUID                     PRIMARY KEY,
    device_id       UUID                     NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    ip_address      VARCHAR(45)              NOT NULL,
    scan_type       VARCHAR(20)              NOT NULL,
    requested_ports VARCHAR(2000)            NOT NULL,
    port_count      INTEGER                  NOT NULL DEFAULT 0,
    status          VARCHAR(20)              NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at     TIMESTAMP WITH TIME ZONE,
    open_count      INTEGER                  NOT NULL DEFAULT 0,
    closed_count    INTEGER                  NOT NULL DEFAULT 0,
    filtered_count  INTEGER                  NOT NULL DEFAULT 0,
    error_message   VARCHAR(1000)
);

CREATE INDEX idx_port_scans_device ON port_scans (device_id, started_at DESC);
CREATE INDEX idx_port_scans_status ON port_scans (status);

CREATE TABLE port_scan_results (
    id             UUID         PRIMARY KEY,
    scan_id        UUID         NOT NULL REFERENCES port_scans (id) ON DELETE CASCADE,
    port           INTEGER      NOT NULL,
    state          VARCHAR(20)  NOT NULL,
    previous_state VARCHAR(20),
    newly_opened   BOOLEAN      NOT NULL DEFAULT FALSE,
    service        VARCHAR(100)
);

CREATE INDEX idx_port_scan_results_scan ON port_scan_results (scan_id, port);

-- Situação atual de cada porta já verificada em cada dispositivo.
CREATE TABLE device_ports (
    device_id            UUID                     NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    port                 INTEGER                  NOT NULL,
    state                VARCHAR(20)              NOT NULL,
    service              VARCHAR(100),
    open_since           TIMESTAMP WITH TIME ZONE,
    last_checked_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_state_change_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (device_id, port)
);

CREATE INDEX idx_device_ports_state ON device_ports (state, port);
