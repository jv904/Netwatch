-- Fase 10: relatórios emitidos (conteúdo guardado como JSON para o histórico não mudar).

CREATE TABLE reports (
    id           UUID                     PRIMARY KEY,
    report_type  VARCHAR(20)              NOT NULL,
    title        VARCHAR(300)             NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end   TIMESTAMP WITH TIME ZONE NOT NULL,
    period_label VARCHAR(100)             NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trigger_kind VARCHAR(20)              NOT NULL,
    content      VARCHAR(1000000)         NOT NULL
);

CREATE INDEX idx_reports_generated ON reports (generated_at DESC);
CREATE INDEX idx_reports_type ON reports (report_type, period_start);
