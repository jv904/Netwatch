package com.netwatch.netwatch_api.ports;

import org.springframework.stereotype.Component;

import com.netwatch.netwatch_api.common.net.PortState;
import com.netwatch.netwatch_api.common.net.TcpConnect;
import com.netwatch.netwatch_api.settings.SettingKey;
import com.netwatch.netwatch_api.settings.SettingsService;

@Component
public class TcpPortProber implements PortProber {

    private final SettingsService settings;

    public TcpPortProber(SettingsService settings) {
        this.settings = settings;
    }

    /** O tempo de espera é lido a cada porta: uma alteração na tela vale já na próxima verificação. */
    @Override
    public PortState probe(String ip, int port) {
        return TcpConnect.check(ip, port, settings.integer(SettingKey.PORTS_TIMEOUT));
    }
}
