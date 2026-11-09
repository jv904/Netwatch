package com.netwatch.netwatch_api.discovery;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração da descoberta de dispositivos ({@code netwatch.discovery.*}).
 *
 * @param authorizedNetworks faixas CIDR que podem ser varridas; vazio = redes privadas das interfaces locais
 * @param maxHosts           tamanho máximo de uma faixa (proteção contra varreduras amplas)
 * @param concurrency        quantos hosts são sondados ao mesmo tempo
 * @param probeTimeout       tempo máximo de espera por resposta de cada sondagem
 * @param tcpPorts           portas usadas para detectar hosts (conexão aceita ou recusada = host presente)
 * @param resolveHostnames   tentar obter o nome via DNS reverso
 * @param schedule           descoberta periódica
 */
@ConfigurationProperties("netwatch.discovery")
public record DiscoveryProperties(
        @DefaultValue List<String> authorizedNetworks,
        @DefaultValue("1024") int maxHosts,
        @DefaultValue("64") int concurrency,
        @DefaultValue("800ms") Duration probeTimeout,
        @DefaultValue({"445", "80", "443", "22", "139", "3389", "62078", "8080"}) List<Integer> tcpPorts,
        @DefaultValue("true") boolean resolveHostnames,
        @DefaultValue Schedule schedule) {

    public record Schedule(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("15m") Duration interval) {
    }
}
