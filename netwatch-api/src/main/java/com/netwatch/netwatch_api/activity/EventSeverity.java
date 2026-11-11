package com.netwatch.netwatch_api.activity;

/** Importância de um evento para quem acompanha a rede. */
public enum EventSeverity {
    /** Registro de rotina (execução concluída, dispositivo voltou...). */
    INFO,
    /** Mudança que merece ser notada (IP alterado, porta fechada, falha). */
    NOTICE,
    /** Mudança relevante para a segurança (equipamento desconhecido, porta passou a aceitar conexões). */
    WARNING
}
