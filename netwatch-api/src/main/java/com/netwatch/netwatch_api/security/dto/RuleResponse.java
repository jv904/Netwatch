package com.netwatch.netwatch_api.security.dto;

import java.util.List;

import com.netwatch.netwatch_api.security.AlertSeverity;
import com.netwatch.netwatch_api.security.RuleParam;
import com.netwatch.netwatch_api.security.RuleSettings;
import com.netwatch.netwatch_api.security.RuleType;

public record RuleResponse(
        RuleType code,
        RuleType.Category category,
        String title,
        String description,
        String recommendation,
        boolean enabled,
        AlertSeverity severity,
        AlertSeverity defaultSeverity,
        List<Param> params,
        long activeAlerts) {

    public record Param(String name, String label, String description, RuleParam.Type type, String value,
            String defaultValue) {
    }

    public static RuleResponse from(RuleSettings s, long activeAlerts) {
        RuleType t = s.type();
        return new RuleResponse(t, t.category(), t.title(), t.description(), t.recommendation(), s.enabled(),
                s.severity(), t.defaultSeverity(),
                t.params().stream().map(p -> new Param(p.name(), p.label(), p.description(), p.type(),
                        s.value(p.name()), p.defaultValue())).toList(),
                activeAlerts);
    }
}
