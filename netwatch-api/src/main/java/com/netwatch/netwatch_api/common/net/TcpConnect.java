package com.netwatch.netwatch_api.common.net;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

/**
 * Tentativa de conexão TCP (three-way handshake) sem enviar dados.
 * A conexão é fechada imediatamente após ser estabelecida.
 */
public final class TcpConnect {

    private TcpConnect() {
    }

    public static PortState check(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return PortState.OPEN;
        } catch (ConnectException e) {
            String message = e.getMessage();
            return message != null && message.toLowerCase(Locale.ROOT).contains("refused")
                    ? PortState.CLOSED
                    : PortState.FILTERED;
        } catch (IOException e) {
            return PortState.FILTERED;
        }
    }
}
