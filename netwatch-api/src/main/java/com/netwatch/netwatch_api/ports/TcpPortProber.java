package com.netwatch.netwatch_api.ports;

import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.common.net.TcpConnect;

@Component
public class TcpPortProber implements PortProber {

    private final PortScanProperties properties;

    public TcpPortProber(PortScanProperties properties) {
        this.properties = properties;
    }

    @Override
    public PortState probe(String ip, int port) {
        return TcpConnect.check(ip, port, (int) properties.timeout().toMillis());
    }
}
