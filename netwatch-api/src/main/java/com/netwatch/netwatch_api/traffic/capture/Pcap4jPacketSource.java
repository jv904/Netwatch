package com.netwatch.netwatch_api.traffic.capture;

import java.io.EOFException;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.core.PcapStat;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.namednumber.DataLinkType;
import org.pcap4j.util.LinkLayerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Captura real via pcap4j. Depende do Npcap (Windows) ou da libpcap (Linux/macOS).
 * <p>
 * A biblioteca nativa só é carregada na primeira consulta de {@link #status()}; se ela não existir,
 * a captura fica indisponível e o restante da aplicação continua funcionando.
 */
@Component
public class Pcap4jPacketSource implements PacketSource {

    private static final Logger log = LoggerFactory.getLogger(Pcap4jPacketSource.class);

    /** Espera máxima por pacote; define com que frequência o pedido de parada é verificado. */
    private static final int READ_TIMEOUT_MS = 500;

    private final Clock clock;
    private volatile SourceStatus status;

    public Pcap4jPacketSource(Clock clock) {
        this.clock = clock;
    }

    @Override
    public SourceStatus status() {
        SourceStatus current = status;
        if (current == null) {
            synchronized (this) {
                if (status == null) {
                    status = probe();
                }
                current = status;
            }
        }
        return current;
    }

    private SourceStatus probe() {
        try {
            configureNpcapPath();
            String version = Pcaps.libVersion();
            log.info("Biblioteca de captura carregada: {}", version);
            return SourceStatus.available(version);
        } catch (LinkageError | RuntimeException e) {
            log.warn("Captura de pacotes indisponível: {}", e.toString());
            return SourceStatus.unavailable(isWindows()
                    ? "Npcap não encontrado. Instale o Npcap (npcap.com) e reinicie a API."
                    : "libpcap não encontrada. Instale a libpcap e reinicie a API.");
        }
    }

    /** O Npcap instala o wpcap.dll em System32\Npcap, fora do caminho padrão de busca de DLLs. */
    private static void configureNpcapPath() {
        if (!isWindows() || System.getProperty("jna.library.path") != null) {
            return;
        }
        String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
        File npcap = new File(systemRoot, "System32\\Npcap");
        if (npcap.isDirectory()) {
            System.setProperty("jna.library.path", npcap.getAbsolutePath());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public List<CaptureInterface> interfaces() {
        if (!status().available()) {
            return List.of();
        }
        try {
            List<CaptureInterface> result = new ArrayList<>();
            for (PcapNetworkInterface nif : Pcaps.findAllDevs()) {
                result.add(toInterface(nif));
            }
            return result;
        } catch (PcapNativeException e) {
            throw new CaptureException("Não foi possível listar as interfaces de rede: " + e.getMessage(), e);
        }
    }

    private static CaptureInterface toInterface(PcapNetworkInterface nif) {
        List<InterfaceAddress> addresses = new ArrayList<>();
        for (PcapAddress address : nif.getAddresses()) {
            if (address.getAddress() instanceof Inet4Address ip) {
                addresses.add(new InterfaceAddress(ip.getHostAddress(), prefixLength(address.getNetmask())));
            }
        }
        String mac = nif.getLinkLayerAddresses().stream()
                .map(LinkLayerAddress::toString)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .findFirst().orElse(null);
        String description = nif.getDescription() != null ? nif.getDescription() : nif.getName();
        return new CaptureInterface(nif.getName(), description, List.copyOf(addresses), mac, nif.isLoopBack());
    }

    private static int prefixLength(InetAddress netmask) {
        if (!(netmask instanceof Inet4Address)) {
            return 32;
        }
        int bits = 0;
        for (byte b : netmask.getAddress()) {
            bits += Integer.bitCount(b & 0xff);
        }
        return bits;
    }

    @Override
    public CaptureStats capture(String interfaceName, String bpfFilter, int snaplen, boolean promiscuous,
            Consumer<PacketMeta> sink, BooleanSupplier stopRequested) {
        if (!status().available()) {
            throw new CaptureException(status().reason());
        }
        PcapHandle handle;
        try {
            PcapNetworkInterface nif = Pcaps.getDevByName(interfaceName);
            if (nif == null) {
                throw new CaptureException("Interface de rede não encontrada: " + interfaceName);
            }
            handle = nif.openLive(snaplen,
                    promiscuous ? PromiscuousMode.PROMISCUOUS : PromiscuousMode.NONPROMISCUOUS, READ_TIMEOUT_MS);
        } catch (PcapNativeException e) {
            throw new CaptureException("Não foi possível abrir a interface: " + e.getMessage(), e);
        }

        try {
            if (!DataLinkType.EN10MB.equals(handle.getDlt())) {
                throw new CaptureException("A interface não é Ethernet/Wi-Fi (tipo de enlace " + handle.getDlt()
                        + ") e não pode ser analisada.");
            }
            handle.setFilter(bpfFilter, BpfCompileMode.OPTIMIZE);

            while (!stopRequested.getAsBoolean()) {
                byte[] frame;
                try {
                    frame = handle.getNextRawPacketEx();
                } catch (TimeoutException e) {
                    continue;
                } catch (EOFException e) {
                    break;
                }
                PacketParser.parse(frame, handle.getOriginalLength(), clock.instant()).ifPresent(sink);
            }
            return stats(handle);
        } catch (PcapNativeException | NotOpenException e) {
            throw new CaptureException("A captura falhou: " + e.getMessage(), e);
        } finally {
            handle.close();
        }
    }

    private static CaptureStats stats(PcapHandle handle) {
        try {
            PcapStat stat = handle.getStats();
            return new CaptureStats(stat.getNumPacketsReceived(), stat.getNumPacketsDropped());
        } catch (PcapNativeException | NotOpenException | RuntimeException e) {
            return CaptureStats.NONE;
        }
    }
}
