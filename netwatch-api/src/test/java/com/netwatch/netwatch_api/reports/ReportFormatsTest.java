package com.netwatch.netwatch_api.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.netwatch.netwatch_api.common.exception.InvalidInputException;
import com.netwatch.netwatch_api.reports.ReportDocument.Chart;
import com.netwatch.netwatch_api.reports.ReportDocument.Kpi;
import com.netwatch.netwatch_api.reports.ReportDocument.Point;
import com.netwatch.netwatch_api.reports.ReportDocument.Section;
import com.netwatch.netwatch_api.reports.ReportPeriod.Preset;

class ReportFormatsTest {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    /** Quarta-feira, 11/11/2026 10:00 em São Paulo. */
    static final Instant NOW = Instant.parse("2026-11-11T13:00:00Z");

    @Test
    void previousWeekIsMondayToSunday() {
        ReportPeriod p = ReportPeriod.resolve(Preset.PREVIOUS_WEEK, null, null, NOW, SP);
        assertThat(p.from()).isEqualTo(Instant.parse("2026-11-02T03:00:00Z"));
        assertThat(p.to()).isEqualTo(Instant.parse("2026-11-09T03:00:00Z"));
        assertThat(p.label()).isEqualTo("02/11/2026 a 08/11/2026");
    }

    @Test
    void previousMonthIsComplete() {
        ReportPeriod p = ReportPeriod.resolve(Preset.PREVIOUS_MONTH, null, null, NOW, SP);
        assertThat(p.label()).isEqualTo("01/10/2026 a 31/10/2026");
        assertThat(p.contains(Instant.parse("2026-10-31T23:00:00Z"))).isTrue();
        assertThat(p.contains(Instant.parse("2026-11-01T03:00:00Z"))).isFalse();
    }

    @Test
    void customPeriodIsValidated() {
        ReportPeriod p = ReportPeriod.resolve(Preset.CUSTOM, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5), NOW, SP);
        assertThat(p.label()).isEqualTo("05/11/2026");
        assertThatThrownBy(() -> ReportPeriod.resolve(Preset.CUSTOM, null, null, NOW, SP))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ReportPeriod.resolve(Preset.CUSTOM, LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 1), NOW, SP)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ReportPeriod.resolve(Preset.CUSTOM, LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 1, 1), NOW, SP)).isInstanceOf(InvalidInputException.class);
        assertThat(ReportPeriod.resolve(null, null, null, NOW, SP).label()).startsWith("04/11/2026");
    }

    static ReportDocument sample() {
        return new ReportDocument("Relatório de teste", "Descrição", NOW, NOW, "01/11/2026 a 07/11/2026", NOW,
                "America/Sao_Paulo", List.of(
                        new Section("Resumo", "Tudo certo; nada a declarar.", List.of(Kpi.of("Dispositivos", "132")),
                                new Chart("Fabricantes", "BAR", "dispositivos", List.of(new Point("Intelbras", 12))),
                                List.of()),
                        new Section("Detalhe", null, List.of(), null, List.of(ReportBuilder.table("Itens",
                                List.of("Nome", "Obs"),
                                List.of(List.of("Impressora; RH", "diz \"olá\""), List.of("=SOMA(A1)", "-5")))))));
    }

    @Test
    void csvOpensInExcelAndEscapes() {
        byte[] bytes = CsvRenderer.render(sample(), null);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csv).contains("Período;01/11/2026 a 07/11/2026")
                .contains("Dispositivos;132")
                .contains("\"Impressora; RH\";\"diz \"\"olá\"\"\"")
                .contains("'=SOMA(A1);-5");

        String onlyTable = new String(CsvRenderer.render(sample(), 0), StandardCharsets.UTF_8);
        assertThat(onlyTable).doesNotContain("Período").contains("Nome;Obs");
    }

    @Test
    void pdfIsGeneratedWithPortugueseText() {
        byte[] pdf = PdfRenderer.render(sample());
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(PdfRenderer.safe("IP alterado: a → b ✓ ação")).isEqualTo("IP alterado: a -> b v ação");
    }
}
