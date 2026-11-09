package com.netwatch.netwatch_api.discovery.probe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.discovery.Ipv4Network;

/** DNS reverso (PTR). Muitos roteadores domésticos publicam o nome dos clientes DHCP assim. */
@Component
public class ReverseDnsHostnameResolver implements HostnameResolver {

    @Override
    public Optional<String> resolve(String ipAddress) {
        var value = Ipv4Network.toInt(ipAddress);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        int ip = value.getAsInt();
        byte[] bytes = {(byte) (ip >>> 24), (byte) (ip >>> 16), (byte) (ip >>> 8), (byte) ip};
        try {
            String name = InetAddress.getByAddress(bytes).getCanonicalHostName();
            // Quando não há nome, o Java devolve o próprio IP.
            return name == null || name.equals(ipAddress) ? Optional.empty() : Optional.of(name);
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
