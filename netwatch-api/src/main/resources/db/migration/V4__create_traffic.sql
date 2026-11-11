-- Fase 5: captura de pacotes. Só metadados agregados; o conteúdo dos pacotes nunca é armazenado.

CREATE TABLE capture_sessions (
    id                    UUID                     PRIMARY KEY,
    interface_name        VARCHAR(255)             NOT NULL,
    interface_description VARCHAR(255),
    local_address         VARCHAR(45),
    bpf_filter            VARCHAR(1000)            NOT NULL,
    status                VARCHAR(20)              NOT NULL,
    started_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    planned_end_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at           TIMESTAMP WITH TIME ZONE,
    packets               BIGINT                   NOT NULL DEFAULT 0,
    bytes                 BIGINT                   NOT NULL DEFAULT 0,
    dropped_packets       BIGINT                   NOT NULL DEFAULT 0,
    new_devices           INTEGER                  NOT NULL DEFAULT 0,
    error_message         VARCHAR(1000)
);

CREATE INDEX idx_capture_sessions_started ON capture_sessions (started_at DESC);
CREATE INDEX idx_capture_sessions_status ON capture_sessions (status);

-- Fluxo = pacotes com o mesmo protocolo, origem e destino (IP e porta), enquanto houver atividade.
CREATE TABLE traffic_flows (
    id            UUID                     PRIMARY KEY,
    session_id    UUID                     NOT NULL REFERENCES capture_sessions (id) ON DELETE CASCADE,
    protocol      VARCHAR(10)              NOT NULL,
    src_ip        VARCHAR(45)              NOT NULL,
    src_port      INTEGER,
    dst_ip        VARCHAR(45)              NOT NULL,
    dst_port      INTEGER,
    service_port  INTEGER,
    src_mac       VARCHAR(17),
    dst_mac       VARCHAR(17),
    src_device_id UUID                     REFERENCES devices (id) ON DELETE SET NULL,
    dst_device_id UUID                     REFERENCES devices (id) ON DELETE SET NULL,
    packets       BIGINT                   NOT NULL DEFAULT 0,
    bytes         BIGINT                   NOT NULL DEFAULT 0,
    tcp_flags     INTEGER                  NOT NULL DEFAULT 0,
    first_seen    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_traffic_flows_last_seen ON traffic_flows (last_seen DESC);
CREATE INDEX idx_traffic_flows_src_ip ON traffic_flows (src_ip, last_seen);
CREATE INDEX idx_traffic_flows_dst_ip ON traffic_flows (dst_ip, last_seen);
CREATE INDEX idx_traffic_flows_src_device ON traffic_flows (src_device_id, last_seen);
CREATE INDEX idx_traffic_flows_dst_device ON traffic_flows (dst_device_id, last_seen);
CREATE INDEX idx_traffic_flows_service ON traffic_flows (protocol, service_port);
CREATE INDEX idx_traffic_flows_session ON traffic_flows (session_id);

-- Totais por minuto e protocolo (alimenta o gráfico de vazão).
CREATE TABLE traffic_stats_minute (
    id           UUID                     PRIMARY KEY,
    bucket_start TIMESTAMP WITH TIME ZONE NOT NULL,
    protocol     VARCHAR(10)              NOT NULL,
    packets      BIGINT                   NOT NULL DEFAULT 0,
    bytes        BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT uk_traffic_stats_minute UNIQUE (bucket_start, protocol)
);
