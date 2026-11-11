package com.netwatch.netwatch_api.security;

/**
 * Situação de um alerta. O motor de segurança (Fase 7) só cria alertas OPEN; o gerenciamento
 * (reconhecer, resolver, descartar) é da Fase 8.
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    DISMISSED;

    /** Ainda em aberto: uma nova ocorrência soma neste alerta em vez de criar outro. */
    public boolean isActive() {
        return this == OPEN || this == ACKNOWLEDGED;
    }
}
