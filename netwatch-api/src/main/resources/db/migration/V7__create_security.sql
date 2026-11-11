-- Fase 7: motor de segurança (regras e alertas).

CREATE TABLE security_rules (
    code       VARCHAR(40)              PRIMARY KEY,
    enabled    BOOLEAN                  NOT NULL DEFAULT TRUE,
    severity   VARCHAR(20)              NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE security_rule_params (
    rule_code   VARCHAR(40)   NOT NULL REFERENCES security_rules (code) ON DELETE CASCADE,
    param_name  VARCHAR(40)   NOT NULL,
    param_value VARCHAR(1000) NOT NULL,
    PRIMARY KEY (rule_code, param_name)
);

CREATE TABLE security_alerts (
    id            UUID                     PRIMARY KEY,
    rule_code     VARCHAR(40)              NOT NULL,
    severity      VARCHAR(20)              NOT NULL,
    status        VARCHAR(20)              NOT NULL,
    title         VARCHAR(300)             NOT NULL,
    evidence      VARCHAR(2000),
    device_id     UUID                     REFERENCES devices (id) ON DELETE SET NULL,
    ip_address    VARCHAR(45),
    dedup_key     VARCHAR(200)             NOT NULL,
    occurrences   INTEGER                  NOT NULL DEFAULT 1,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_security_alerts_status ON security_alerts (status, last_seen_at DESC);
CREATE INDEX idx_security_alerts_dedup ON security_alerts (dedup_key, status);
CREATE INDEX idx_security_alerts_device ON security_alerts (device_id, last_seen_at DESC);
CREATE INDEX idx_security_alerts_rule ON security_alerts (rule_code, status);
