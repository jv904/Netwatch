package com.netwatch.netwatch_api.discovery.probe;

import java.util.Optional;

/** Obtém o nome de um equipamento a partir do IP. */
public interface HostnameResolver {

    Optional<String> resolve(String ipAddress);
}
