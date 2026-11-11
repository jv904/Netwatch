package com.netwatch.netwatch_api.reports;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.netwatch.netwatch_api.reports.ReportDocument.Kpi;
import com.netwatch.netwatch_api.reports.ReportDocument.Section;
import com.netwatch.netwatch_api.reports.ReportDocument.Table;

/**
 * CSV no formato que o Excel em português abre direto: separador ponto e vírgula e UTF-8 com BOM.
 * Contém os indicadores e todas as tabelas, uma após a outra, separadas por linha em branco.
 */
final class CsvRenderer {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvRenderer() {
    }

    /**
     * @param tableIndex se informado, só aquela tabela (contando todas as seções em ordem, a partir de 0)
     */
    static byte[] render(ReportDocument doc, Integer tableIndex) {
        StringBuilder out = new StringBuilder();
        if (tableIndex == null) {
            line(out, List.of(doc.title()));
            line(out, List.of("Período", doc.periodLabel()));
            out.append("\r\n");
        }
        int index = 0;
        for (Section section : doc.sections()) {
            if (tableIndex == null && !section.kpis().isEmpty()) {
                line(out, List.of(section.title()));
                for (Kpi kpi : section.kpis()) {
                    line(out, kpi.hint() != null ? List.of(kpi.label(), kpi.value(), kpi.hint())
                            : List.of(kpi.label(), kpi.value()));
                }
                out.append("\r\n");
            }
            for (Table table : section.tables()) {
                if (tableIndex == null || tableIndex == index) {
                    if (tableIndex == null) {
                        line(out, List.of(section.title() + " — " + table.title()));
                    }
                    line(out, table.columns());
                    table.rows().forEach(row -> line(out, row));
                    if (table.totalRows() > table.rows().size()) {
                        line(out, List.of("(+" + (table.totalRows() - table.rows().size()) + " linhas não incluídas)"));
                    }
                    out.append("\r\n");
                }
                index++;
            }
        }
        byte[] body = out.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    private static void line(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(';');
            }
            out.append(escape(cells.get(i)));
        }
        out.append("\r\n");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        // Evita que o Excel interprete o conteúdo como fórmula (injeção de CSV).
        String v = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 && !value.matches("-?[\\d.,]+")
                ? "'" + value : value;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
