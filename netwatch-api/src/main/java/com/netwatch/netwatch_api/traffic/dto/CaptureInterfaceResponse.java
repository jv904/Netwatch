package com.netwatch.netwatch_api.traffic.dto;

import java.util.List;

import com.netwatch.netwatch_api.traffic.CaptureService.InterfaceOption;
import com.netwatch.netwatch_api.traffic.capture.PacketSource.InterfaceAddress;

/**
 * @param eligible          a interface tem endereço em rede autorizada e pode ser capturada
 * @param authorizedAddress o endereço desta máquina na rede autorizada (ex.: 192.168.100.20/24)
 */
public record CaptureInterfaceResponse(
        String name,
        String description,
        String macAddress,
        List<String> addresses,
        boolean eligible,
        String authorizedAddress) {

    public static CaptureInterfaceResponse from(InterfaceOption option) {
        InterfaceAddress authorized = option.authorizedAddress();
        return new CaptureInterfaceResponse(
                option.iface().name(),
                option.iface().description(),
                option.iface().macAddress(),
                option.iface().addresses().stream().map(a -> a.ipAddress() + "/" + a.prefixLength()).toList(),
                option.eligible(),
                authorized != null ? authorized.ipAddress() + "/" + authorized.prefixLength() : null);
    }
}
