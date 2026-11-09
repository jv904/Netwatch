package com.netwatch.netwatch_api.discovery.probe;

/** Verifica se existe um equipamento respondendo em determinado IP. */
public interface HostProber {

    boolean isAlive(String ipAddress);
}
