package com.netwatch.netwatch_api.ports;

public enum PortScanType {
    /** Portas mais comuns ({@link KnownServices#COMMON_PORTS}). */
    COMMON,
    /** Portas informadas pelo usuário. */
    CUSTOM
}
