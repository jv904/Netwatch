package com.netwatch.netwatch_api.traffic.capture;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Fonte de pacotes da rede. A implementação real usa pcap4j + Npcap/libpcap;
 * os testes usam uma fonte simulada.
 */
public interface PacketSource {

    /** A biblioteca de captura está instalada e carregou? */
    SourceStatus status();

    /** Interfaces de rede que podem ser capturadas. Vazio se a captura estiver indisponível. */
    List<CaptureInterface> interfaces();

    /**
     * Captura até {@code stopRequested} retornar verdadeiro (verificado pelo menos a cada meio segundo).
     * Bloqueia a thread chamadora.
     *
     * @param bpfFilter filtro BPF aplicado no driver (antes de os pacotes chegarem à JVM)
     * @param snaplen   bytes capturados por pacote: o suficiente para os cabeçalhos, não para o conteúdo
     * @throws CaptureException se a interface não puder ser aberta ou a captura falhar
     */
    CaptureStats capture(String interfaceName, String bpfFilter, int snaplen, boolean promiscuous,
            Consumer<PacketMeta> sink, BooleanSupplier stopRequested);

    record SourceStatus(boolean available, String libraryVersion, String reason) {

        public static SourceStatus available(String version) {
            return new SourceStatus(true, version, null);
        }

        public static SourceStatus unavailable(String reason) {
            return new SourceStatus(false, null, reason);
        }
    }

    /**
     * @param name        identificador técnico (no Windows, "\Device\NPF_{...}")
     * @param description nome amigável do adaptador
     */
    record CaptureInterface(String name, String description, List<InterfaceAddress> addresses, String macAddress,
            boolean loopback) {
    }

    record InterfaceAddress(String ipAddress, int prefixLength) {
    }

    /** Contadores do driver ao fim da captura (zero se o driver não informar). */
    record CaptureStats(long received, long dropped) {

        public static final CaptureStats NONE = new CaptureStats(0, 0);
    }

    class CaptureException extends RuntimeException {

        public CaptureException(String message) {
            super(message);
        }

        public CaptureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
