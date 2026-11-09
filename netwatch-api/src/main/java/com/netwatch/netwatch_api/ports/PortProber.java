package com.netwatch.netwatch_api.ports;

import com.netwatch.netwatch_api.common.net.PortState;

/** Verifica o estado de uma porta TCP. */
public interface PortProber {

    PortState probe(String ip, int port);
}
