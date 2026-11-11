package com.netwatch.netwatch_api.reports;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netwatch.netwatch_api.common.web.PageResponse;
import com.netwatch.netwatch_api.reports.ReportPeriod.Preset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    public record ReportTypeResponse(ReportType type, String title, String description) {
    }

    /**
     * @param period período pronto; com CUSTOM, informe {@code from} e {@code to} (datas locais, inclusive)
     */
    public record GenerateRequest(@NotNull(message = "Informe o tipo de relatório.") ReportType type,
            Preset period, LocalDate from, LocalDate to) {
    }

    public record ReportSummary(UUID id, ReportType type, String title, Instant periodStart, Instant periodEnd,
            String periodLabel, Instant generatedAt, Report.Trigger trigger) {

        static ReportSummary from(Report r) {
            return new ReportSummary(r.getId(), r.getType(), r.getTitle(), r.getPeriodStart(), r.getPeriodEnd(),
                    r.getPeriodLabel(), r.getGeneratedAt(), r.getTrigger());
        }
    }

    public record ReportResponse(ReportSummary report, ReportDocument document) {
    }

    @GetMapping("/types")
    public List<ReportTypeResponse> types() {
        return Arrays.stream(ReportType.values())
                .map(t -> new ReportTypeResponse(t, t.title(), t.description())).toList();
    }

    /** Emite um relatório com os dados atuais do período (fica guardado no histórico). */
    @PostMapping
    public ResponseEntity<ReportSummary> generate(@Valid @RequestBody GenerateRequest request) {
        Report report = reports.generate(request.type(), request.period(), request.from(), request.to(),
                Report.Trigger.MANUAL);
        return ResponseEntity.created(URI.create("/api/reports/" + report.getId())).body(ReportSummary.from(report));
    }

    @GetMapping
    public PageResponse<ReportSummary> history(@RequestParam(required = false) ReportType type,
            @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(reports.history(type, pageable), ReportSummary::from);
    }

    @GetMapping("/{id}")
    public ReportResponse get(@PathVariable UUID id) {
        Report report = reports.get(id);
        return new ReportResponse(ReportSummary.from(report), reports.document(report));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        Report report = reports.get(id);
        return download(PdfRenderer.render(reports.document(report)), MediaType.APPLICATION_PDF, fileName(report, "pdf"));
    }

    /**
     * @param table só uma tabela (índice, a partir de 0); sem ele, o relatório inteiro
     */
    @GetMapping("/{id}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable UUID id, @RequestParam(required = false) Integer table) {
        Report report = reports.get(id);
        return download(CsvRenderer.render(reports.document(report), table),
                new MediaType("text", "csv", StandardCharsets.UTF_8), fileName(report, "csv"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reports.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String fileName) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    /** "netwatch-relatorio-geral-da-rede-2026-11-03.pdf" */
    static String fileName(Report report, String extension) {
        String slug = Normalizer.normalize(report.getTitle(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String date = report.getPeriodStart().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        return "netwatch-" + slug + "-" + date + "." + extension;
    }
}
