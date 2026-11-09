package com.netwatch.netwatch_api.discovery.probe;

import java.util.List;

/** Endereços IPv4 configurados nas interfaces de rede desta máquina. */
public interface LocalInterfaces {

    List<LocalAddress> list();

    /**
     * @param interfaceName nome técnico da interface
     * @param displayName   nome amigável (ex.: "Intel(R) Wi-Fi 6 AX201")
     * @param ipAddress     IPv4 da máquina nesta interface
     * @param prefixLength  tamanho do prefixo da sub-rede
     * @param macAddress    MAC da interface (formato aa:bb:..), pode ser nulo
     * @param virtual       interface provavelmente virtual (WSL, Docker, VPN, VM...)
     */
    record LocalAddress(
            String interfaceName,
            String displayName,
            String ipAddress,
            int prefixLength,
            String macAddress,
            boolean virtual) {
    }
}
