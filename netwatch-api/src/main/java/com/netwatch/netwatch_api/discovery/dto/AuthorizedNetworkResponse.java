package com.netwatch.netwatch_api.discovery.dto;

import com.netwatch.netwatch_api.discovery.AuthorizedNetworks.AuthorizedNetwork;

/**
 * @param cidr      faixa autorizada
 * @param source    origem: nome da interface local ou "Configuração"
 * @param hostCount quantidade de endereços verificados
 * @param virtual   interface provavelmente virtual (WSL, Docker, VPN...)
 */
public record AuthorizedNetworkResponse(String cidr, String source, long hostCount, boolean virtual) {

    public static AuthorizedNetworkResponse from(AuthorizedNetwork network) {
        return new AuthorizedNetworkResponse(network.network().toString(), network.source(),
                network.network().hostCount(), network.virtual());
    }
}
