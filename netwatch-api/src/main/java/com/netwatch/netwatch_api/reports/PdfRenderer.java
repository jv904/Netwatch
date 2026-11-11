package com.netwatch.netwatch_api.reports;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.netwatch.netwatch_api.reports.ReportDocument.Chart;
import com.netwatch.netwatch_api.reports.ReportDocument.Kpi;
import com.netwatch.netwatch_api.reports.ReportDocument.Point;
import com.netwatch.netwatch_api.reports.ReportDocument.Section;
import com.netwatch.netwatch_api.reports.ReportDocument.Table;

/** Gera o PDF de um relatório (A4; paisagem quando há tabelas largas). */
final class PdfRenderer {

    private static final Color INK = new Color(0x1f, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6b, 0x72, 0x80);
    private static final Color ACCENT = new Color(0x39, 0x87, 0xe5);
    private static final Color HEADER_BG = new Color(0xee, 0xf2, 0xf7);
    private static final Color ZEBRA = new Color(0xf8, 0xfa, 0xfc);
    private static final Color BORDER = new Color(0xd8, 0xde, 0xe6);
    private static final Color GOOD = new Color(0x1f, 0x9d, 0x55);
    private static final Color WARNING = new Color(0xc2, 0x7c, 0x0e);
    private static final Color CRITICAL = new Color(0xc0, 0x39, 0x2b);
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private final BaseFont regular;
    private final BaseFont bold;

    private PdfRenderer() throws IOException {
        regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
    }

    static byte[] render(ReportDocument doc) {
        try {
            return new PdfRenderer().write(doc);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Não foi possível gerar o PDF: " + e.getMessage(), e);
        }
    }

    private byte[] write(ReportDocument doc) throws DocumentException {
        boolean wide = doc.sections().stream().flatMap(s -> s.tables().stream()).anyMatch(t -> t.columns().size() > 6);
        Document pdf = new Document(wide ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 40, 44);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(pdf, out);
        Fmt fmt = new Fmt(ZoneId.of(doc.timeZone()));
        writer.setPageEvent(new Footer(doc.title() + " · gerado em " + fmt.dateTime(doc.generatedAt())));
        pdf.addTitle(doc.title());
        pdf.addCreator("NetWatch");
        pdf.open();

        pdf.add(paragraph("NetWatch", font(bold, 9, ACCENT), 0));
        pdf.add(paragraph(doc.title(), font(bold, 18, INK), 2));
        pdf.add(paragraph("Período: " + doc.periodLabel(), font(regular, 10, MUTED), 2));
        pdf.add(paragraph(doc.description(), font(regular, 9.5f, MUTED), 10));

        for (Section section : doc.sections()) {
            Paragraph title = paragraph(section.title(), font(bold, 13, INK), 4);
            title.setSpacingBefore(10);
            pdf.add(title);
            if (section.summary() != null) {
                pdf.add(paragraph(section.summary(), font(regular, 10, INK), 6));
            }
            if (!section.kpis().isEmpty()) {
                pdf.add(kpis(section.kpis()));
            }
            if (section.chart() != null && !section.chart().points().isEmpty()) {
                pdf.add(chart(section.chart()));
            }
            for (Table table : section.tables()) {
                if (!table.rows().isEmpty()) {
                    pdf.add(table(table));
                }
            }
        }
        pdf.close();
        return out.toByteArray();
    }

    private PdfPTable kpis(List<Kpi> kpis) throws DocumentException {
        int columns = Math.min(4, kpis.size());
        PdfPTable grid = new PdfPTable(columns);
        grid.setWidthPercentage(100);
        grid.setSpacingAfter(8);
        for (Kpi kpi : kpis) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(BORDER);
            cell.setPadding(7);
            cell.addElement(paragraph(kpi.label(), font(regular, 8.5f, MUTED), 1));
            cell.addElement(paragraph(kpi.value(), font(bold, 15, toneColor(kpi.tone())), 1));
            if (kpi.hint() != null) {
                cell.addElement(paragraph(kpi.hint(), font(regular, 8, MUTED), 0));
            }
            grid.addCell(cell);
        }
        // Completa a última linha para a tabela não descartar células.
        int remainder = kpis.size() % columns;
        if (remainder != 0) {
            for (int i = remainder; i < columns; i++) {
                PdfPCell empty = new PdfPCell();
                empty.setBorder(Rectangle.NO_BORDER);
                grid.addCell(empty);
            }
        }
        return grid;
    }

    /** Gráfico como barras horizontais (funciona igual para categorias e para a série por dia). */
    private PdfPTable chart(Chart chart) throws DocumentException {
        double max = chart.points().stream().mapToDouble(Point::value).max().orElse(1);
        PdfPTable table = new PdfPTable(new float[] {32, 52, 16});
        table.setWidthPercentage(100);
        table.setSpacingAfter(8);
        PdfPCell header = cell(chart.title() + (chart.unit() != null ? " (" + chart.unit() + ")" : ""),
                font(bold, 9.5f, INK), null);
        header.setColspan(3);
        header.setBorder(Rectangle.NO_BORDER);
        header.setPaddingBottom(4);
        table.addCell(header);
        for (Point p : chart.points()) {
            PdfPCell label = cell(p.label(), font(regular, 8.5f, INK), null);
            label.setBorder(Rectangle.NO_BORDER);
            table.addCell(label);

            float share = max <= 0 ? 0 : (float) (p.value() / max);
            PdfPTable bar = new PdfPTable(new float[] {Math.max(share, 0.001f), Math.max(1 - share, 0.001f)});
            bar.setWidthPercentage(100);
            PdfPCell filled = new PdfPCell(new Phrase(" ", font(regular, 5, INK)));
            filled.setBackgroundColor(share > 0 ? ACCENT : null);
            filled.setBorder(Rectangle.NO_BORDER);
            filled.setFixedHeight(7);
            PdfPCell rest = new PdfPCell(new Phrase(" ", font(regular, 5, INK)));
            rest.setBorder(Rectangle.NO_BORDER);
            bar.addCell(filled);
            bar.addCell(rest);
            PdfPCell barCell = new PdfPCell(bar);
            barCell.setBorder(Rectangle.NO_BORDER);
            barCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            barCell.setPadding(3);
            table.addCell(barCell);

            String value = "bytes".equals(chart.unit()) ? Fmt.bytes((long) p.value()) : Fmt.num((long) p.value());
            PdfPCell valueCell = cell(value, font(regular, 8.5f, INK), null);
            valueCell.setBorder(Rectangle.NO_BORDER);
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(valueCell);
        }
        return table;
    }

    private PdfPTable table(Table data) throws DocumentException {
        PdfPTable table = new PdfPTable(data.columns().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(2);
        table.setSpacingAfter(8);
        PdfPCell title = cell(data.title() + (data.totalRows() > data.rows().size()
                ? " (primeiras " + data.rows().size() + " de " + Fmt.num(data.totalRows()) + ")" : ""),
                font(bold, 9.5f, INK), null);
        title.setColspan(data.columns().size());
        title.setBorder(Rectangle.NO_BORDER);
        title.setPaddingBottom(4);
        table.addCell(title);
        for (String column : data.columns()) {
            PdfPCell c = cell(column, font(bold, 8.5f, INK), HEADER_BG);
            table.addCell(c);
        }
        int i = 0;
        for (List<String> row : data.rows()) {
            Color bg = i++ % 2 == 1 ? ZEBRA : null;
            for (String value : row) {
                table.addCell(cell(value, font(regular, 8.5f, INK), bg));
            }
        }
        return table;
    }

    private PdfPCell cell(String text, Font font, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setBorderColor(BORDER);
        cell.setPadding(4);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        return cell;
    }

    private Paragraph paragraph(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(safe(text), font);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private static Font font(BaseFont base, float size, Color color) {
        return new Font(base, size, Font.NORMAL, color);
    }

    private static Color toneColor(String tone) {
        return switch (tone == null ? "" : tone) {
            case "good" -> GOOD;
            case "warning" -> WARNING;
            case "critical" -> CRITICAL;
            default -> INK;
        };
    }

    /** A fonte padrão do PDF cobre o português (windows-1252); outros símbolos viram equivalentes simples. */
    static String safe(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace("→", "->").replace("←", "<-").replace("✓", "v");
        StringBuilder sb = new StringBuilder(t.length());
        t.codePoints().forEach(cp -> {
            String s = new String(Character.toChars(cp));
            sb.append(CP1252.newEncoder().canEncode(s) ? s : "?");
        });
        return sb.toString();
    }

    /** Rodapé com o título do relatório e a numeração das páginas. */
    private final class Footer extends PdfPageEventHelper {

        private final String text;

        Footer(String text) {
            this.text = safe(text);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            var cb = writer.getDirectContent();
            cb.beginText();
            cb.setFontAndSize(regular, 7.5f);
            cb.setColorFill(MUTED);
            cb.showTextAligned(Element.ALIGN_LEFT, text, document.left(), document.bottom() - 18, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "Página " + writer.getPageNumber(), document.right(),
                    document.bottom() - 18, 0);
            cb.endText();
        }
    }
}
