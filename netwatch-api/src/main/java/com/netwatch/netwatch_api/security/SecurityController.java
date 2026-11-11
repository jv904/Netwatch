package com.netwatch.netwatch_api.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.security.AlertService.AlertFilter;
import com.netwatch.netwatch_api.security.dto.AlertActionRequests.BulkRequest;
import com.netwatch.netwatch_api.security.dto.AlertActionRequests.CommentRequest;
import com.netwatch.netwatch_api.security.dto.AlertActionRequests.DismissRequest;
import com.netwatch.netwatch_api.security.dto.AlertActionRequests.NoteRequest;
import com.netwatch.netwatch_api.security.dto.AlertCountsResponse;
import com.netwatch.netwatch_api.security.dto.AlertDetailResponse;
import com.netwatch.netwatch_api.security.dto.AlertResponse;
import com.netwatch.netwatch_api.security.dto.BulkResultResponse;
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
            @RequestParam(required = false) String state,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) RuleType rule,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 25, sort = "lastSeenAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(
                alerts.search(new AlertFilter(status, state, severity, rule, deviceId, q), pageable), a -> a);
    }

    /** Contadores por situação e ativos por gravidade (contador do menu e avisos de alerta novo). */
    @GetMapping("/alerts/counts")
    public AlertCountsResponse counts() {
        return alerts.counts();
    }

    /** Alerta com a linha do tempo (comentários e mudanças de situação). */
    @GetMapping("/alerts/{id}")
    public AlertDetailResponse alert(@PathVariable UUID id) {
        return alerts.detail(id);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public AlertDetailResponse acknowledge(@PathVariable UUID id,
            @Valid @RequestBody(required = false) NoteRequest request) {
        alerts.acknowledge(id, request != null ? request.note() : null);
        return alerts.detail(id);
    }

    @PostMapping("/alerts/{id}/resolve")
    public AlertDetailResponse resolve(@PathVariable UUID id,
            @Valid @RequestBody(required = false) NoteRequest request) {
        alerts.resolve(id, request != null ? request.note() : null);
        return alerts.detail(id);
    }

    /** Descarta (falso positivo, risco aceito...). Com {@code suppress}, o mesmo problema não abre alerta novo. */
    @PostMapping("/alerts/{id}/dismiss")
    public AlertDetailResponse dismiss(@PathVariable UUID id, @Valid @RequestBody DismissRequest request) {
        alerts.dismiss(id, request.reason(), request.note(), request.suppressRepeats());
        return alerts.detail(id);
    }

    @PostMapping("/alerts/{id}/reopen")
    public AlertDetailResponse reopen(@PathVariable UUID id,
            @Valid @RequestBody(required = false) NoteRequest request) {
        alerts.reopen(id, request != null ? request.note() : null);
        return alerts.detail(id);
    }

    @PostMapping("/alerts/{id}/comments")
    public ResponseEntity<AlertDetailResponse> comment(@PathVariable UUID id,
            @Valid @RequestBody CommentRequest request) {
        alerts.comment(id, request.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(alerts.detail(id));
    }

    /** Mesma ação em vários alertas; os que não podem mudar são contados como ignorados. */
    @PostMapping("/alerts/bulk")
    public BulkResultResponse bulk(@Valid @RequestBody BulkRequest request) {
        return alerts.bulk(request.ids(), request.action(), request.note(), request.reason(),
                request.suppressRepeats());
    }

    /** Alertas ativos por gravidade e por regra, e os mais recentes. */
    @GetMapping("/summary")
    public SecuritySummaryResponse summary() {
        return alerts.summary(rules.all());
    }
}
