package com.netwatch.netwatch_api.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;

class TcpConnectTest {

    @Test
    void openPortIsDetected() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            assertThat(TcpConnect.check("127.0.0.1", server.getLocalPort(), 1000)).isEqualTo(PortState.OPEN);
        }
    }

    @Test
    void closedPortIsDetected() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        assertThat(TcpConnect.check("127.0.0.1", port, 1000)).isEqualTo(PortState.CLOSED);
    }
}
