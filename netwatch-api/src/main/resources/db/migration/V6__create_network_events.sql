-- Fase 6: histórico de atividades da rede.

CREATE TABLE network_events (
    id           UUID                     PRIMARY KEY,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type   VARCHAR(40)              NOT NULL,
    category     VARCHAR(20)              NOT NULL,
    severity     VARCHAR(20)              NOT NULL,
    device_id    UUID                     REFERENCES devices (id) ON DELETE SET NULL,
    ip_address   VARCHAR(45),
    title        VARCHAR(300)             NOT NULL,
    details      VARCHAR(2000),
    reference_id UUID
);

CREATE INDEX idx_network_events_occurred ON network_events (occurred_at DESC);
CREATE INDEX idx_network_events_device ON network_events (device_id, occurred_at DESC);
CREATE INDEX idx_network_events_type ON network_events (event_type, occurred_at DESC);
CREATE INDEX idx_network_events_severity ON network_events (severity, occurred_at DESC);
