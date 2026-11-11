package com.netwatch.netwatch_api.traffic;

public enum CaptureSessionStatus {
    /** Capturando. */
    RUNNING,
    /** Terminou ao atingir a duração planejada. */
    COMPLETED,
    /** Parada pelo usuário. */
    STOPPED,
    /** Erro do driver/interface ou aplicação reiniciada no meio. */
    FAILED
}
