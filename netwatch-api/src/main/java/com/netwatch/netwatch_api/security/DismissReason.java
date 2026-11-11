package com.netwatch.netwatch_api.security;

/** Por que um alerta foi descartado. */
public enum DismissReason {
    /** A regra se enganou: não há problema. */
    FALSE_POSITIVE("Falso positivo"),
    /** O problema existe, mas foi aceito conscientemente (ex.: compartilhamento necessário). */
    ACCEPTED_RISK("Risco aceito"),
    OTHER("Outro motivo");

    private final String label;

    DismissReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
