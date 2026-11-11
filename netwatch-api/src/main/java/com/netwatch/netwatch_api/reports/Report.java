package com.netwatch.netwatch_api.reports;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um relatório emitido. O conteúdo ({@link ReportDocument}) fica congelado em JSON. */
@Entity
@Table(name = "reports")
public class Report {

    public enum Trigger {
        MANUAL, SCHEDULED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType type;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "period_label", nullable = false, length = 100)
    private String periodLabel;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trigger_kind", nullable = false, length = 20)
    private Trigger trigger;

    @Column(nullable = false, length = 1_000_000)
    private String content;

    protected Report() {
    }

    Report(ReportType type, String title, ReportPeriod period, Instant generatedAt, Trigger trigger, String content) {
        this.type = type;
        this.title = title;
        this.periodStart = period.from();
        this.periodEnd = period.to();
        this.periodLabel = period.label();
        this.generatedAt = generatedAt;
        this.trigger = trigger;
        this.content = content;
    }

    public UUID getId() { return id; }
    public ReportType getType() { return type; }
    public String getTitle() { return title; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public String getPeriodLabel() { return periodLabel; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Trigger getTrigger() { return trigger; }
    public String getContent() { return content; }
}
