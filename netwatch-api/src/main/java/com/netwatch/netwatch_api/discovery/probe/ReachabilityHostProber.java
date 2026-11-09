package com.netwatch.netwatch_api.discovery.probe;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.common.net.TcpConnect;
import com.netwatch.netwatch_api.discovery.DiscoveryProperties;

/**
 * Detecta hosts sem privilégio de administrador:
 * <ul>
 *   <li>ICMP via {@link InetAddress#isReachable(int)};</li>
 *   <li>tentativas de conexão TCP em portas comuns — tanto conexão aceita quanto
 *       <i>recusada</i> provam que existe um equipamento no endereço.</li>
 * </ul>
 * Nenhum dado é enviado: a conexão é aberta e fechada imediatamente.
 */
@Component
public class ReachabilityHostProber implements HostProber {

    private final DiscoveryProperties properties;

    public ReachabilityHostProber(DiscoveryProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAlive(String ipAddress) {
        int timeoutMs = (int) properties.probeTimeout().toMillis();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            attempts.add(executor.submit(() -> icmp(ipAddress, timeoutMs)));
            for (int port : properties.tcpPorts()) {
                attempts.add(executor.submit(() -> tcp(ipAddress, port, timeoutMs)));
            }
            for (Future<Boolean> attempt : attempts) {
                if (Boolean.TRUE.equals(attempt.get())) {
                    attempts.forEach(f -> f.cancel(true));
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    private static boolean icmp(String ip, int timeoutMs) {
        try {
            return InetAddress.getByName(ip).isReachable(timeoutMs);
        } catch (IOException e) {
            return false;
        }
    }

    /** Conexão aceita ou recusada ("Connection refused" = o host respondeu com RST) prova que o host existe. */
    private static boolean tcp(String ip, int port, int timeoutMs) {
        return TcpConnect.check(ip, port, timeoutMs) != PortState.FILTERED;
    }
}
