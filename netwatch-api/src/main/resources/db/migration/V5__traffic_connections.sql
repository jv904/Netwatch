-- Fase 5b: tráfego sem driver, a partir da tabela de conexões do sistema.

ALTER TABLE capture_sessions ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'PACKETS';

ALTER TABLE traffic_flows ADD COLUMN process_name VARCHAR(255);
ALTER TABLE traffic_flows ADD COLUMN connection_state VARCHAR(20);

-- Maior número de conexões simultâneas observado no minuto.
ALTER TABLE traffic_stats_minute ADD COLUMN connections BIGINT NOT NULL DEFAULT 0;
