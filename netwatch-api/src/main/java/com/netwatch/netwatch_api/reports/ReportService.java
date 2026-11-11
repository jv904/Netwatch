package com.netwatch.netwatch_api.reports;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.netwatch.netwatch_api.common.exception.ResourceNotFoundException;
import com.netwatch.netwatch_api.reports.ReportPeriod.Preset;

import tools.jackson.databind.ObjectMapper;

/** Emissão, histórico e agendamento de relatórios. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reports;
    private final ReportBuilder builder;
    private final ObjectMapper json;
    private final ReportProperties properties;
    private final Clock clock;

    public ReportService(ReportRepository reports, ReportBuilder builder, ObjectMapper json,
            ReportProperties properties, Clock clock) {
        this.reports = reports;
        this.builder = builder;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Report generate(ReportType type, Preset preset, LocalDate from, LocalDate to, Report.Trigger trigger) {
        ReportPeriod period = ReportPeriod.resolve(preset, from, to, clock.instant(), ZoneId.systemDefault());
        ReportDocument document = builder.build(type, period);
        Report report = reports.save(new Report(type, type.title(), period, document.generatedAt(), trigger,
                json.writeValueAsString(document)));
        log.info("Relatório {} emitido ({}): {}", type, trigger, period.label());
        return report;
    }

    public Report get(UUID id) {
        return reports.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relatório não encontrado: " + id));
    }

    public ReportDocument document(Report report) {
        return json.readValue(report.getContent(), ReportDocument.class);
    }

    public Page<Report> history(ReportType type, Pageable pageable) {
        return type != null ? reports.findByType(type, pageable) : reports.findAll(pageable);
    }

    @Transactional
    public void delete(UUID id) {
        reports.delete(get(id));
    }

    // ------------------------------------------------------------------ agendamento

    /** Segunda-feira, 6h: relatório geral da semana anterior. */
    @Scheduled(cron = "0 0 6 * * MON")
    public void weekly() {
        if (properties.weekly()) {
            scheduled(Preset.PREVIOUS_WEEK);
        }
    }

    /** Dia 1º, 6h30: relatório geral do mês anterior. */
    @Scheduled(cron = "0 30 6 1 * *")
    public void monthly() {
        if (properties.monthly()) {
            scheduled(Preset.PREVIOUS_MONTH);
        }
    }

    /** Emite o relatório agendado, se ainda não existir para o mesmo período (ex.: API reiniciada). */
    @Transactional
    public Report scheduled(Preset preset) {
        ReportPeriod period = ReportPeriod.resolve(preset, null, null, clock.instant(), ZoneId.systemDefault());
        if (reports.existsByTypeAndTriggerAndPeriodStartAndPeriodEnd(ReportType.OVERVIEW, Report.Trigger.SCHEDULED,
                period.from(), period.to())) {
            return null;
        }
        try {
            return generate(ReportType.OVERVIEW, preset, null, null, Report.Trigger.SCHEDULED);
        } catch (RuntimeException e) {
            log.error("Falha ao emitir o relatório agendado {}", preset, e);
            return null;
        }
    }

    /** Apaga relatórios mais antigos que {@code netwatch.reports.retention-days}. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT12H")
    @Transactional
    public void purge() {
        var cutoff = clock.instant().minus(Duration.ofDays(Math.max(1, properties.retentionDays())));
        reports.findAll().stream().filter(r -> r.getGeneratedAt().isBefore(cutoff)).forEach(reports::delete);
    }
}
