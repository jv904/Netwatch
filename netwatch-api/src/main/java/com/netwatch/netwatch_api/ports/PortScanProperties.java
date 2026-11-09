package com.netwatch.netwatch_api.ports;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param timeout     espera máxima por porta
 * @param concurrency conexões simultâneas (somando todas as verificações em andamento)
 * @param maxPorts    máximo de portas em uma verificação personalizada
 */
@ConfigurationProperties("netwatch.ports")
public record PortScanProperties(
        @DefaultValue("1500ms") Duration timeout,
        @DefaultValue("100") int concurrency,
        @DefaultValue("1024") int maxPorts) {
}
