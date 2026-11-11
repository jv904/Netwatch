package com.netwatch.netwatch_api.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param weekly        gera o relatório geral da semana anterior toda segunda-feira, às 6h
 * @param monthly       gera o relatório geral do mês anterior todo dia 1º, às 6h30
 * @param retentionDays dias que os relatórios emitidos ficam guardados
 */
@ConfigurationProperties("netwatch.reports")
public record ReportProperties(
        @DefaultValue("true") boolean weekly,
        @DefaultValue("true") boolean monthly,
        @DefaultValue("365") int retentionDays) {
}
