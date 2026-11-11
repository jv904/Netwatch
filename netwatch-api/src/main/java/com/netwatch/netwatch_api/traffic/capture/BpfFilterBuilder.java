package com.netwatch.netwatch_api.traffic.capture;

import java.util.Collection;
import java.util.stream.Collectors;

import com.netwatch.netwatch_api.discovery.Ipv4Network;

/**
 * Monta o filtro BPF que restringe a captura às redes autorizadas.
 * O filtro roda no driver, então pacotes de fora dessas redes nem chegam à aplicação.
 * {@code net X} casa IPv4 e ARP com origem ou destino na faixa X.
 */
public final class BpfFilterBuilder {

    private BpfFilterBuilder() {
    }

    public static String build(Collection<Ipv4Network> networks) {
        if (networks == null || networks.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma rede autorizada para capturar.");
        }
        return networks.stream()
                .distinct()
                .map(n -> "net " + n)
                .collect(Collectors.joining(" or "));
    }
}
