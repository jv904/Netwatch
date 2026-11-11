-- Fase 11: configurações alteradas pela tela (sem linha = valor do application.yaml).

CREATE TABLE app_settings (
    setting_key   VARCHAR(60)              PRIMARY KEY,
    setting_value VARCHAR(2000)            NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
