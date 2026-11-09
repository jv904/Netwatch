package com.netwatch.netwatch_api.discovery.probe;

import java.util.Map;

/** Tabela ARP do sistema operacional: IP → MAC dos vizinhos vistos recentemente. */
public interface ArpTable {

    Map<String, String> read();
}
