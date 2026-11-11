package com.netwatch.netwatch_api.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.security.AlertService.AlertFilter;
import com.netwatch.netwatch_api.security.dto.AlertResponse;
import com.netwatch.netwatch_api.security.dto.RuleResponse;
import com.netwatch.netwatch_api.security.dto.SecuritySummaryResponse;
import com.netwatch.netwatch_api.security.dto.UpdateRuleRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final SecurityRuleService rules;
    private final AlertService alerts;

    public SecurityController(SecurityRuleService rules, AlertService alerts) {
        this.rules = rules;
        this.alerts = alerts;
    }

    /** Regras do motor com a configuração atual e os alertas ativos de cada uma. */
    @GetMapping("/rules")
    public List<RuleResponse> rules() {
        Map<RuleType, Long> active = alerts.activeByRule();
        return rules.all().stream().map(r -> RuleResponse.from(r, active.getOrDefault(r.type(), 0L))).toList();
    }

    @PutMapping("/rules/{code}")
    public RuleResponse update(@PathVariable RuleType code, @Valid @RequestBody UpdateRuleRequest request) {
        RuleSettings saved = rules.update(code, request.enabled(), request.severity(), request.params());
        return RuleResponse.from(saved, alerts.activeByRule().getOrDefault(code, 0L));
    }

    /** Alertas gerados. Ex.: {@code /api/security/alerts?status=OPEN&severity=CRITICAL}. */
    @GetMapping("/alerts")
    public PageResponse<AlertResponse> alerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) RuleType rule,
            @RequestParam(required = false) UUID deviceId,
            @PageableDefault(size = 25, sort = "lastSeenAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(alerts.search(new AlertFilter(status, severity, rule, deviceId), pageable), a -> a);
    }

    /** Alertas ativos por gravidade e por regra, e os mais recentes. */
    @GetMapping("/summary")
    public SecuritySummaryResponse summary() {
        return alerts.summary(rules.all());
    }
}
