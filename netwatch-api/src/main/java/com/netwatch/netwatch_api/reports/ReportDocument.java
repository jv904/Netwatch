package com.netwatch.netwatch_api.reports;

import java.time.Instant;
import java.util.List;

/**
 * Conteúdo de um relatório, independente do formato: a tela, o PDF e o CSV são gerados a partir dele.
 * Guardado como JSON quando o relatório é emitido, para que o histórico não mude se os dados mudarem.
 *
 * @param periodLabel período em texto (ex.: "03/11/2026 a 09/11/2026")
 * @param timeZone    fuso usado nas datas do relatório
 */
public record ReportDocument(
        String title,
        String description,
        Instant periodStart,
        Instant periodEnd,
        String periodLabel,
        Instant generatedAt,
        String timeZone,
        List<Section> sections) {

    /**
     * Uma seção do relatório.
     *
     * @param summary texto curto de conclusão (pode ser nulo)
     */
    public record Section(String title, String summary, List<Kpi> kpis, Chart chart, List<Table> tables) {
    }

    /**
     * Indicador numérico.
     *
     * @param tone neutral, good, warning ou critical
     */
    public record Kpi(String label, String value, String hint, String tone) {

        public static Kpi of(String label, String value) {
            return new Kpi(label, value, null, "neutral");
        }

        public static Kpi of(String label, String value, String hint, String tone) {
            return new Kpi(label, value, hint, tone);
        }
    }

    /**
     * @param kind BAR (categorias) ou LINE (série no tempo)
     */
    public record Chart(String title, String kind, String unit, List<Point> points) {
    }

    public record Point(String label, double value) {
    }

    /**
     * @param totalRows linhas existentes; maior que {@code rows.size()} quando a tabela foi limitada
     */
    public record Table(String title, List<String> columns, List<List<String>> rows, int totalRows) {
    }
}
