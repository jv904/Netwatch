-- Fase 8: gerenciamento de alertas (ciclo de vida, comentários e supressão de falso positivo).

ALTER TABLE security_alerts ADD COLUMN status_changed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE security_alerts ADD COLUMN resolution_note VARCHAR(2000);
ALTER TABLE security_alerts ADD COLUMN dismiss_reason VARCHAR(30);
ALTER TABLE security_alerts ADD COLUMN suppressed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE security_alerts SET status_changed_at = first_seen_at WHERE status_changed_at IS NULL;

-- Linha do tempo de cada alerta: comentários e mudanças de situação.
CREATE TABLE alert_notes (
    id          UUID                     PRIMARY KEY,
    alert_id    UUID                     NOT NULL REFERENCES security_alerts (id) ON DELETE CASCADE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    kind        VARCHAR(20)              NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    text        VARCHAR(2000)
);

CREATE INDEX idx_alert_notes_alert ON alert_notes (alert_id, created_at);
