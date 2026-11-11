package com.netwatch.netwatch_api.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuração efetiva de uma regra: o que o usuário ajustou, completado com os valores de fábrica.
 */
public record RuleSettings(RuleType type, boolean enabled, AlertSeverity severity, Map<String, String> params) {

    public String value(String name) {
        type.param(name);
        return params.getOrDefault(name, type.param(name).defaultValue());
    }

    public int intValue(String name) {
        return Integer.parseInt(value(name));
    }

    public Set<Integer> ports(String name) {
        return RuleParam.parsePorts(value(name));
    }

    public List<String> ips(String name) {
        String v = value(name);
        return v.isBlank() ? List.of() : List.of(v.split(","));
    }
}
