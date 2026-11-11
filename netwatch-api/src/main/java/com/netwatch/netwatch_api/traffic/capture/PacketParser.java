package com.netwatch.netwatch_api.traffic.capture;

import java.time.Instant;
import java.util.Optional;

import com.netwatch.netwatch_api.traffic.TrafficProtocol;

/**
 * Lê os cabeçalhos de um quadro Ethernet. Interpreta somente IPv4 (com TCP, UDP, ICMP e IGMP)
 * e ARP; o restante é ignorado. Nenhum byte depois dos cabeçalhos de transporte é examinado.
 */
public final class PacketParser {

    private static final int ETHERNET_HEADER = 14;
    private static final int TYPE_IPV4 = 0x0800;
    private static final int TYPE_ARP = 0x0806;
    private static final int TYPE_VLAN = 0x8100;

    private PacketParser() {
    }

    /**
     * @param frame          bytes capturados (podem estar truncados pelo snaplen)
     * @param originalLength tamanho real do quadro na rede; se nulo, usa o tamanho capturado
     */
    public static Optional<PacketMeta> parse(byte[] frame, Integer originalLength, Instant timestamp) {
        if (frame == null || frame.length < ETHERNET_HEADER) {
            return Optional.empty();
        }
        String dstMac = mac(frame, 0);
        String srcMac = mac(frame, 6);
        int offset = 12;
        int type = u16(frame, offset);
        if (type == TYPE_VLAN && frame.length >= ETHERNET_HEADER + 4) {
            offset += 4;
            type = u16(frame, offset);
        }
        offset += 2;
        int length = originalLength != null && originalLength > 0 ? originalLength : frame.length;

        return switch (type) {
            case TYPE_IPV4 -> ipv4(frame, offset, srcMac, dstMac, length, timestamp);
            case TYPE_ARP -> arp(frame, offset, dstMac, length, timestamp);
            default -> Optional.empty();
        };
    }

    private static Optional<PacketMeta> ipv4(byte[] f, int off, String srcMac, String dstMac, int length,
            Instant timestamp) {
        if (f.length < off + 20 || (f[off] & 0xf0) != 0x40) {
            return Optional.empty();
        }
        int headerLength = (f[off] & 0x0f) * 4;
        if (headerLength < 20) {
            return Optional.empty();
        }
        int protocolNumber = f[off + 9] & 0xff;
        boolean firstFragment = (u16(f, off + 6) & 0x1fff) == 0;
        String srcIp = ip(f, off + 12);
        String dstIp = ip(f, off + 16);
        int transport = off + headerLength;

        TrafficProtocol protocol = switch (protocolNumber) {
            case 1 -> TrafficProtocol.ICMP;
            case 2 -> TrafficProtocol.IGMP;
            case 6 -> TrafficProtocol.TCP;
            case 17 -> TrafficProtocol.UDP;
            default -> TrafficProtocol.OTHER;
        };

        Integer srcPort = null;
        Integer dstPort = null;
        int flags = 0;
        boolean hasPorts = protocol == TrafficProtocol.TCP || protocol == TrafficProtocol.UDP;
        if (hasPorts && firstFragment && f.length >= transport + 4) {
            srcPort = u16(f, transport);
            dstPort = u16(f, transport + 2);
            if (protocol == TrafficProtocol.TCP && f.length >= transport + 14) {
                flags = f[transport + 13] & 0xff;
            }
        }
        return Optional.of(new PacketMeta(timestamp, srcMac, dstMac, srcIp, dstIp, protocol, srcPort, dstPort,
                length, flags));
    }

    /** ARP sobre Ethernet/IPv4: usa o remetente e o alvo do próprio ARP. */
    private static Optional<PacketMeta> arp(byte[] f, int off, String dstMac, int length, Instant timestamp) {
        if (f.length < off + 28 || u16(f, off) != 1 || u16(f, off + 2) != TYPE_IPV4
                || (f[off + 4] & 0xff) != 6 || (f[off + 5] & 0xff) != 4) {
            return Optional.empty();
        }
        String senderMac = mac(f, off + 8);
        String senderIp = ip(f, off + 14);
        String targetIp = ip(f, off + 24);
        return Optional.of(new PacketMeta(timestamp, senderMac, dstMac, senderIp, targetIp, TrafficProtocol.ARP,
                null, null, length, 0));
    }

    private static int u16(byte[] f, int i) {
        return ((f[i] & 0xff) << 8) | (f[i + 1] & 0xff);
    }

    private static String ip(byte[] f, int i) {
        return (f[i] & 0xff) + "." + (f[i + 1] & 0xff) + "." + (f[i + 2] & 0xff) + "." + (f[i + 3] & 0xff);
    }

    private static String mac(byte[] f, int i) {
        StringBuilder sb = new StringBuilder(17);
        for (int k = 0; k < 6; k++) {
            if (k > 0) {
                sb.append(':');
            }
            int b = f[i + k] & 0xff;
            sb.append(Character.forDigit(b >> 4, 16)).append(Character.forDigit(b & 0x0f, 16));
        }
        return sb.toString();
    }
}
