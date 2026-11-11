package com.netwatch.netwatch_api.traffic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netwatch.netwatch_api.traffic.capture.PacketMeta;

/**
 * Soma o tráfego em fluxos (protocolo + origem + destino) e em totais por minuto,
 * para que o banco receba poucas linhas agregadas em vez de uma por pacote ou por leitura.
 * <p>
 * A thread de captura chama {@link #add} (pacotes) ou {@link #addConnection} (tabela de conexões);
 * a de gravação chama {@link #drain} periodicamente.
 */
final class FlowAggregator {

    record FlowKey(TrafficProtocol protocol, String srcIp, Integer srcPort, String dstIp, Integer dstPort) {
    }

    record StatKey(Instant bucket, TrafficProtocol protocol) {
    }

    /** Acumulado de um fluxo desde o último {@link #drain}. */
    record FlowDelta(FlowKey key, String srcMac, String dstMac, long packets, long bytes, int tcpFlags,
            Instant firstSeen, Instant lastSeen, String processName, String connectionState) {
    }

    /**
     * @param connections maior número de conexões simultâneas visto no minuto
     */
    record StatDelta(Instant bucket, TrafficProtocol protocol, long packets, long bytes, long connections) {
    }

    record Drain(List<FlowDelta> flows, List<StatDelta> stats) {

        boolean isEmpty() {
            return flows.isEmpty() && stats.isEmpty();
        }
    }

    private static final class FlowCounter {
        String srcMac;
        String dstMac;
        long packets;
        long bytes;
        int tcpFlags;
        Instant firstSeen;
        Instant lastSeen;
        String processName;
        String connectionState;

        void seen(Instant at) {
            if (firstSeen == null) {
                firstSeen = at;
            }
            lastSeen = at;
        }
    }

    private static final class StatCounter {
        long packets;
        long bytes;
        long connections;
    }

    private Map<FlowKey, FlowCounter> flows = new HashMap<>();
    private Map<StatKey, StatCounter> stats = new HashMap<>();

    synchronized void add(PacketMeta p) {
        FlowKey key = new FlowKey(p.protocol(), p.srcIp(), p.srcPort(), p.dstIp(), p.dstPort());
        FlowCounter flow = flows.computeIfAbsent(key, k -> new FlowCounter());
        flow.seen(p.timestamp());
        flow.srcMac = p.srcMac();
        flow.dstMac = p.dstMac();
        flow.packets++;
        flow.bytes += p.length();
        flow.tcpFlags |= p.tcpFlags();

        StatCounter stat = stat(p.timestamp(), p.protocol());
        stat.packets++;
        stat.bytes += p.length();
    }

    /** Uma conexão vista na tabela do sistema: mantém o fluxo vivo, sem somar pacotes nem bytes. */
    synchronized void addConnection(FlowKey key, Instant at, String srcMac, String dstMac, String processName,
            String connectionState) {
        FlowCounter flow = flows.computeIfAbsent(key, k -> new FlowCounter());
        flow.seen(at);
        if (srcMac != null) {
            flow.srcMac = srcMac;
        }
        if (dstMac != null) {
            flow.dstMac = dstMac;
        }
        if (processName != null) {
            flow.processName = processName;
        }
        if (connectionState != null) {
            flow.connectionState = connectionState;
        }
    }

    /** Quantas conexões de um protocolo existiam em uma leitura da tabela. */
    synchronized void observeConcurrent(Instant at, TrafficProtocol protocol, long concurrent) {
        StatCounter stat = stat(at, protocol);
        stat.connections = Math.max(stat.connections, concurrent);
    }

    private StatCounter stat(Instant at, TrafficProtocol protocol) {
        return stats.computeIfAbsent(new StatKey(at.truncatedTo(ChronoUnit.MINUTES), protocol), k -> new StatCounter());
    }

    /** Devolve o que foi acumulado e zera os contadores. */
    Drain drain() {
        Map<FlowKey, FlowCounter> drainedFlows;
        Map<StatKey, StatCounter> drainedStats;
        synchronized (this) {
            drainedFlows = flows;
            drainedStats = stats;
            flows = new HashMap<>();
            stats = new HashMap<>();
        }
        List<FlowDelta> flowDeltas = new ArrayList<>(drainedFlows.size());
        drainedFlows.forEach((k, c) -> flowDeltas.add(new FlowDelta(k, c.srcMac, c.dstMac, c.packets, c.bytes,
                c.tcpFlags, c.firstSeen, c.lastSeen, c.processName, c.connectionState)));
        List<StatDelta> statDeltas = new ArrayList<>(drainedStats.size());
        drainedStats.forEach((k, c) -> statDeltas.add(
                new StatDelta(k.bucket(), k.protocol(), c.packets, c.bytes, c.connections)));
        return new Drain(flowDeltas, statDeltas);
    }
}
