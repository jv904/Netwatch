package com.netwatch.netwatch_api.security;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;

/** Configuração das regras do motor de segurança. */
@Service
@Transactional(readOnly = true)
public class SecurityRuleService {

    private final SecurityRuleRepository rules;
    private final Clock clock;

    public SecurityRuleService(SecurityRuleRepository rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    /** Toda regra do catálogo ganha uma linha no banco (ligada, com a gravidade de fábrica). */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureRules() {
        for (RuleType type : RuleType.values()) {
            if (!rules.existsById(type)) {
                rules.save(new SecurityRule(type, clock.instant()));
            }
        }
    }

    public RuleSettings settings(RuleType type) {
        return rules.findById(type).map(SecurityRuleService::toSettings)
                .orElseGet(() -> new RuleSettings(type, true, type.defaultSeverity(), Map.of()));
    }

    public List<RuleSettings> all() {
        return Arrays.stream(RuleType.values()).map(this::settings).toList();
    }

    /**
     * @param params valores informados; parâmetros ausentes voltam ao padrão de fábrica
     */
    @Transactional
    public RuleSettings update(RuleType type, boolean enabled, AlertSeverity severity, Map<String, String> params) {
        if (severity == null) {
            throw new InvalidInputException("Informe a gravidade da regra.");
        }
        Map<String, String> normalized = new HashMap<>();
        if (params != null) {
            params.forEach((name, value) -> {
                RuleParam param = type.params().stream().filter(p -> p.name().equals(name)).findFirst()
                        .orElseThrow(() -> new InvalidInputException(
                                "A regra \"" + type.title() + "\" não tem o parâmetro " + name + "."));
                String v = param.normalize(value);
                if (!v.equals(param.defaultValue())) {
                    normalized.put(name, v);
                }
            });
        }
        SecurityRule rule = rules.findById(type).orElseGet(() -> new SecurityRule(type, clock.instant()));
        rule.update(enabled, severity, normalized, clock.instant());
        return toSettings(rules.save(rule));
    }

    private static RuleSettings toSettings(SecurityRule rule) {
        return new RuleSettings(rule.getCode(), rule.isEnabled(), rule.getSeverity(), rule.getParams());
    }
}
