package com.netwatch.netwatch_api.traffic.connections;

import com.netwatch.netwatch_api.traffic.TrafficProtocol;

/**
 * Uma linha da tabela de conexões do sistema (IPv4).
 *
 * @param remoteIp    nulo quando não há ponta remota (ex.: UDP {@code *:*})
 * @param state       estado TCP (ESTABLISHED, LISTENING...); nulo para UDP
 * @param processName nome do executável dono da conexão, quando conhecido
 */
public record ConnectionEntry(
        TrafficProtocol protocol,
        String localIp,
        int localPort,
        String remoteIp,
        Integer remotePort,
        String state,
        Integer pid,
        String processName) {

    public ConnectionEntry withProcessName(String name) {
        return new ConnectionEntry(protocol, localIp, localPort, remoteIp, remotePort, state, pid, name);
    }

    public boolean isListening() {
        return "LISTENING".equals(state) || "LISTEN".equals(state);
    }
}
