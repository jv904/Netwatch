CREATE TABLE discovery_runs (
    id             UUID                     PRIMARY KEY,
    cidr           VARCHAR(18)              NOT NULL,
    status         VARCHAR(20)              NOT NULL,
    run_trigger    VARCHAR(20)              NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at    TIMESTAMP WITH TIME ZONE,
    hosts_scanned  INTEGER                  NOT NULL DEFAULT 0,
    devices_found  INTEGER                  NOT NULL DEFAULT 0,
    new_devices    INTEGER                  NOT NULL DEFAULT 0,
    inactivated    INTEGER                  NOT NULL DEFAULT 0,
    error_message  VARCHAR(1000)
);

CREATE INDEX idx_discovery_runs_started_at ON discovery_runs (started_at DESC);
CREATE INDEX idx_discovery_runs_status ON discovery_runs (status);
