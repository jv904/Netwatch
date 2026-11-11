package com.netwatch.netwatch_api.traffic.connections;

import java.util.List;

/**
 * Tabela de conexões do sistema operacional: alternativa à captura de pacotes que não exige
 * driver nem privilégios. Mostra só as conexões desta máquina, sem contar pacotes nem bytes.
 */
public interface ConnectionTable {

    boolean available();

    /** Conexões IPv4 neste instante (inclui as em LISTENING, usadas para saber a direção). */
    List<ConnectionEntry> snapshot();
}
