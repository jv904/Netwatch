CREATE TABLE devices (
    id            UUID                     PRIMARY KEY,
    ip_address    VARCHAR(45)              NOT NULL,
    mac_address   VARCHAR(17),
    hostname      VARCHAR(255),
    vendor        VARCHAR(255),
    custom_name   VARCHAR(100),
    device_type   VARCHAR(30)              NOT NULL,
    status        VARCHAR(20)              NOT NULL,
    recognized    BOOLEAN                  NOT NULL DEFAULT FALSE,
    notes         VARCHAR(1000),
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at  TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT uk_devices_mac_address UNIQUE (mac_address)
);

CREATE INDEX idx_devices_ip_address ON devices (ip_address);
CREATE INDEX idx_devices_status ON devices (status);
CREATE INDEX idx_devices_recognized ON devices (recognized);
