package com.netwatch.netwatch_api.discovery.dto;

import jakarta.validation.constraints.Size;

/** @param cidr faixa a verificar (ex.: 192.168.0.0/24); vazio = rede autorizada padrão */
public record StartDiscoveryRequest(@Size(max = 18) String cidr) {
}
