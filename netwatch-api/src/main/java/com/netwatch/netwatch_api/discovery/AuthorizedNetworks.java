package com.netwatch.netwatch_api.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces;
import com.netwatch.netwatch_api.discovery.probe.LocalInterfaces.LocalAddress;

/**
 * Define quais redes o NetWatch pode varrer.
 * <p>
 * Regras: apenas faixas privadas (RFC 1918), contidas em uma rede autorizada e com no máximo
 * {@code maxHosts} endereços. Sem configuração explícita, as redes autorizadas são as sub-redes
 * privadas das interfaces desta máquina.
 */
@Component
public class AuthorizedNetworks {

    /** Redes locais maiores que isso são reduzidas ao /24 do host. */
    private static final int MIN_AUTO_PREFIX = 22;

    private final DiscoveryProperties properties;
    private final LocalInterfaces localInterfaces;

    public AuthorizedNetworks(DiscoveryProperties properties, LocalInterfaces localInterfaces) {
        this.properties = properties;
        this.localInterfaces = localInterfaces;
    }

    public record AuthorizedNetwork(Ipv4Network network, String source, boolean virtual) {
    }

    public List<AuthorizedNetwork> list() {
        if (!properties.authorizedNetworks().isEmpty()) {
            List<AuthorizedNetwork> configured = new ArrayList<>();
            for (String cidr : properties.authorizedNetworks()) {
                Ipv4Network network = Ipv4Network.parse(cidr);
                if (!network.isPrivate()) {
                    throw new IllegalStateException(
                            "netwatch.discovery.authorized-networks contém uma faixa não privada: " + cidr);
                }
                configured.add(new AuthorizedNetwork(network, "Configuração", false));
            }
            return configured;
        }
        return detectLocalNetworks();
    }

    /** O IP pertence a alguma rede autorizada? */
    public boolean isAuthorized(String ip) {
        return list().stream().anyMatch(a -> a.network().contains(ip));
    }

    /**
     * Valida a faixa solicitada. Se {@code cidr} for nulo/vazio, usa a primeira rede autorizada.
     */
    public Ipv4Network resolve(String cidr) {
        List<AuthorizedNetwork> authorized = list();
        if (cidr == null || cidr.isBlank()) {
            if (authorized.isEmpty()) {
                throw new InvalidInputException(
                        "Nenhuma rede autorizada foi encontrada. Configure netwatch.discovery.authorized-networks.");
            }
            return limit(authorized.getFirst().network());
        }

        Ipv4Network requested = Ipv4Network.parse(cidr);
        if (!requested.isPrivate()) {
            throw new InvalidInputException("Somente redes privadas podem ser verificadas: " + requested + ".");
        }
        if (requested.hostCount() > properties.maxHosts()) {
            throw new InvalidInputException("A faixa " + requested + " tem " + requested.hostCount()
                    + " endereços; o máximo permitido é " + properties.maxHosts() + ".");
        }
        boolean allowed = authorized.stream().anyMatch(a -> a.network().contains(requested));
        if (!allowed) {
            throw new InvalidInputException("A faixa " + requested + " não está dentro das redes autorizadas.");
        }
        return requested;
    }

    private Ipv4Network limit(Ipv4Network network) {
        if (network.hostCount() <= properties.maxHosts()) {
            return network;
        }
        throw new InvalidInputException("A rede autorizada " + network
                + " é grande demais para uma verificação completa. Informe uma faixa menor.");
    }

    private List<AuthorizedNetwork> detectLocalNetworks() {
        Map<Ipv4Network, AuthorizedNetwork> unique = new LinkedHashMap<>();
        List<LocalAddress> addresses = new ArrayList<>(localInterfaces.list());
        addresses.sort(Comparator.comparing(LocalAddress::virtual));
        for (LocalAddress address : addresses) {
            if (address.prefixLength() >= 31) {
                continue;
            }
            int prefix = address.prefixLength() < MIN_AUTO_PREFIX ? 24 : address.prefixLength();
            int ip = Ipv4Network.toInt(address.ipAddress()).orElseThrow();
            Ipv4Network network = new Ipv4Network(ip, prefix);
            if (network.isPrivate()) {
                unique.putIfAbsent(network, new AuthorizedNetwork(network, address.displayName(), address.virtual()));
            }
        }
        return List.copyOf(unique.values());
    }
}
