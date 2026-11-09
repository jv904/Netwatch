package com.netwatch.netwatch_api.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.device.DeviceObservation;
import com.netwatch.netwatch_api.discovery.probe.ArpTable;
import com.netwatch.netwatch_api.discovery.probe.HostProber;
import com.netwatch.netwatch_api.discovery.probe.HostnameResolver;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces.LocalAddress;
import com.netwatch.netwatch_api.discovery.vendor.MacVendorLookup;

/**
 * Varre uma faixa autorizada e devolve os equipamentos encontrados.
 * <ol>
 *   <li>sonda todos os IPs em paralelo (ICMP + TCP);</li>
 *   <li>lê a tabela ARP — a sondagem a preenche, revelando até quem bloqueia ping/TCP;</li>
 *   <li>inclui a própria máquina;</li>
 *   <li>completa com hostname (DNS reverso) e fabricante (OUI).</li>
 * </ol>
 */
@Component
public class NetworkScanner {

    private static final Logger log = LoggerFactory.getLogger(NetworkScanner.class);
    private static final long HOSTNAME_TIMEOUT_SECONDS = 3;

    private final HostProber prober;
    private final ArpTable arpTable;
    private final HostnameResolver hostnameResolver;
    private final LocalInterfaces localInterfaces;
    private final MacVendorLookup vendorLookup;
    private final DiscoveryProperties properties;

    public NetworkScanner(HostProber prober, ArpTable arpTable, HostnameResolver hostnameResolver,
            LocalInterfaces localInterfaces, MacVendorLookup vendorLookup, DiscoveryProperties properties) {
        this.prober = prober;
        this.arpTable = arpTable;
        this.hostnameResolver = hostnameResolver;
        this.localInterfaces = localInterfaces;
        this.vendorLookup = vendorLookup;
        this.properties = properties;
    }

    public record ScanResult(int hostsScanned, List<DeviceObservation> observations) {
    }

    public ScanResult scan(Ipv4Network network) throws InterruptedException {
        List<String> hosts = network.hosts();

        // Própria máquina: IP → MAC/nome vindos das interfaces locais.
        Map<String, LocalAddress> self = new HashMap<>();
        for (LocalAddress address : localInterfaces.list()) {
            if (network.contains(address.ipAddress())) {
                self.put(address.ipAddress(), address);
            }
        }

        Set<String> alive = probeAll(hosts, self.keySet());
        Map<String, String> arp = new LinkedHashMap<>();
        arpTable.read().forEach((ip, mac) -> {
            if (network.contains(ip) && !self.containsKey(ip)) {
                arp.put(ip, mac);
            }
        });

        Map<String, String> found = new LinkedHashMap<>(); // IP → MAC (pode ser nulo)
        alive.forEach(ip -> found.put(ip, arp.get(ip)));
        arp.forEach(found::put);
        self.forEach((ip, address) -> found.put(ip, address.macAddress()));

        Map<String, String> hostnames = resolveHostnames(found.keySet());
        String localHostname = localHostname();

        List<DeviceObservation> observations = new ArrayList<>(found.size());
        found.forEach((ip, mac) -> {
            String hostname = self.containsKey(ip) ? localHostname : hostnames.get(ip);
            String vendor = vendorLookup.lookup(mac).orElse(null);
            observations.add(new DeviceObservation(ip, mac, hostname, vendor));
        });
        observations.sort(Comparator.comparingLong(o -> Integer.toUnsignedLong(
                Ipv4Network.toInt(o.ipAddress()).orElse(0))));

        log.info("Descoberta em {}: {} hosts verificados, {} responderam, {} na tabela ARP, {} no total.",
                network, hosts.size(), alive.size(), arp.size(), observations.size());
        return new ScanResult(hosts.size(), observations);
    }

    private Set<String> probeAll(List<String> hosts, Set<String> skip) throws InterruptedException {
        Set<String> alive = ConcurrentHashMap.newKeySet();
        Semaphore permits = new Semaphore(Math.max(1, properties.concurrency()));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String ip : hosts) {
                if (skip.contains(ip)) {
                    continue;
                }
                permits.acquire();
                executor.submit(() -> {
                    try {
                        if (prober.isAlive(ip)) {
                            alive.add(ip);
                        }
                    } finally {
                        permits.release();
                    }
                });
            }
        }
        return alive;
    }

    private Map<String, String> resolveHostnames(Set<String> ips) {
        Map<String, String> result = new ConcurrentHashMap<>();
        if (!properties.resolveHostnames() || ips.isEmpty()) {
            return result;
        }
        // Não usamos try-with-resources: consultas DNS travadas não devem segurar a descoberta.
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<Void>> lookups = ips.stream()
                    .map(ip -> CompletableFuture
                            .supplyAsync(() -> hostnameResolver.resolve(ip), executor)
                            .completeOnTimeout(Optional.empty(), HOSTNAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .exceptionally(e -> Optional.empty())
                            .thenAccept(name -> name.ifPresent(n -> result.put(ip, n))))
                    .toList();
            CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).join();
        } finally {
            executor.shutdown();
        }
        return result;
    }

    private static String localHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }
}
