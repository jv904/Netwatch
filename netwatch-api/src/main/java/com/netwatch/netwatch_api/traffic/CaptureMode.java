package com.netwatch.netwatch_api.traffic;

/** De onde vêm os dados de tráfego de uma sessão. */
public enum CaptureMode {
    /** Captura de pacotes (Npcap/libpcap): vê volume, pacotes e o tráfego de broadcast da rede. */
    PACKETS,
    /** Tabela de conexões do sistema: sem driver, só as conexões desta máquina, sem volume. */
    CONNECTIONS
}
