package com.netwatch.netwatch_api.traffic;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled                liga a observação de tráfego
 * @param mode                   {@code auto} (pacotes se o Npcap/libpcap carregar, senão tabela de conexões),
 *                               {@code packets} ou {@code connections}
 * @param snaplen                bytes lidos de cada pacote; 128 cobre os cabeçalhos e descarta o conteúdo
 * @param promiscuous            modo promíscuo da placa (em redes com switch quase não faz diferença)
 * @param flushInterval          de quanto em quanto tempo os fluxos são gravados no banco
 * @param flowTimeout            inatividade após a qual um fluxo é encerrado (o próximo pacote abre outro)
 * @param connectionPollInterval intervalo entre leituras da tabela de conexões
 * @param defaultDuration        duração de uma sessão quando o usuário não informa
 * @param maxDuration            duração máxima permitida para uma sessão
 * @param retentionDays          dias que os fluxos e estatísticas ficam guardados
 * @param defaultInterface       interface usada quando o usuário não escolhe (nome técnico ou parte da descrição)
 */
@ConfigurationProperties("netwatch.capture")
public record CaptureProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("auto") String mode,
        @DefaultValue("128") int snaplen,
        @DefaultValue("false") boolean promiscuous,
        @DefaultValue("10s") Duration flushInterval,
        @DefaultValue("2m") Duration flowTimeout,
        @DefaultValue("5s") Duration connectionPollInterval,
        @DefaultValue("1h") Duration defaultDuration,
        @DefaultValue("8h") Duration maxDuration,
        @DefaultValue("7") int retentionDays,
        String defaultInterface) {
}
