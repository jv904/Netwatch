package com.netwatch.netwatch_api.traffic.capture;

import java.time.Instant;

import com.netwatch.netwatch_api.traffic.TrafficProtocol;

/**
 * Metadados de um pacote, extraídos apenas dos cabeçalhos (Ethernet, IPv4, TCP/UDP, ARP).
 * O conteúdo (payload) nunca é lido nem armazenado.
 *
 * @param srcPort  porta de origem (só TCP/UDP)
 * @param dstPort  porta de destino (só TCP/UDP)
 * @param length   tamanho do quadro na rede, em bytes
 * @param tcpFlags flags TCP (SYN, ACK, FIN, RST...), 0 nos demais protocolos
 */
public record PacketMeta(
        Instant timestamp,
        String srcMac,
        String dstMac,
        String srcIp,
        String dstIp,
        TrafficProtocol protocol,
        Integer srcPort,
        Integer dstPort,
        int length,
        int tcpFlags) {
}
