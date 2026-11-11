package com.netwatch.netwatch_api.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/** Configuração de uma regra feita pelo usuário (o que não estiver aqui usa o padrão de {@link RuleType}). */
@Entity
@Table(name = "security_rules")
public class SecurityRule {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 40)
    private RuleType code;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "security_rule_params", joinColumns = @JoinColumn(name = "rule_code"))
    @MapKeyColumn(name = "param_name", length = 40)
    @Column(name = "param_value", length = 1000)
    private Map<String, String> params = new HashMap<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SecurityRule() {
    }

    SecurityRule(RuleType code, Instant at) {
        this.code = code;
        this.enabled = true;
        this.severity = code.defaultSeverity();
        this.updatedAt = at;
    }

    void update(boolean enabled, AlertSeverity severity, Map<String, String> params, Instant at) {
        this.enabled = enabled;
        this.severity = severity;
        this.params.clear();
        this.params.putAll(params);
        this.updatedAt = at;
    }

    public RuleType getCode() { return code; }
    public boolean isEnabled() { return enabled; }
    public AlertSeverity getSeverity() { return severity; }
    public Map<String, String> getParams() { return Map.copyOf(params); }
    public Instant getUpdatedAt() { return updatedAt; }
}
