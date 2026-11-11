import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, of, switchMap } from 'rxjs';

import { Api } from '../../core/api';
import { formatDateTime, timeAgo } from '../../core/format';
import { Page, ReportPreset, ReportSummary, ReportType, ReportTypeInfo } from '../../core/models';
import { ToastService } from '../../core/toast';
import { Icon } from '../../shared/icon';
import { Paginator } from '../../shared/paginator';

const PAGE_SIZE = 15;

@Component({
  selector: 'app-reports',
  imports: [Icon, Paginator, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class Reports {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly types = signal<ReportTypeInfo[]>([]);
  protected readonly type = signal<ReportType>('OVERVIEW');
  protected readonly preset = signal<ReportPreset>('LAST_7D');
  protected readonly from = signal(isoDate(-7));
  protected readonly to = signal(isoDate(0));
  protected readonly generating = signal(false);

  protected readonly filter = signal<ReportType | ''>('');
  protected readonly page = signal(0);
  protected readonly history = signal<Page<ReportSummary> | null>(null);
  private readonly reload = signal(0);

  protected readonly formatDateTime = formatDateTime;
  protected readonly timeAgo = timeAgo;
  protected readonly pdfUrl = (id: string) => this.api.reportPdfUrl(id);
  protected readonly csvUrl = (id: string) => this.api.reportCsvUrl(id);

  protected readonly presets: { value: ReportPreset; label: string }[] = [
    { value: 'LAST_24H', label: 'Últimas 24 horas' },
    { value: 'LAST_7D', label: 'Últimos 7 dias' },
    { value: 'LAST_30D', label: 'Últimos 30 dias' },
    { value: 'PREVIOUS_WEEK', label: 'Semana anterior (seg. a dom.)' },
    { value: 'PREVIOUS_MONTH', label: 'Mês anterior' },
    { value: 'CUSTOM', label: 'Personalizado…' },
  ];

  protected readonly typeTitle = computed(() => {
    const map = new Map(this.types().map((t) => [t.type, t.title]));
    return (type: ReportType) => map.get(type) ?? type;
  });

  protected readonly customInvalid = computed(
    () => this.preset() === 'CUSTOM' && (!this.from() || !this.to() || this.to() < this.from()),
  );

  constructor() {
    this.api
      .reportTypes()
      .pipe(catchError(() => of([] as ReportTypeInfo[])))
      .subscribe((t) => this.types.set(t));

    combineLatest([toObservable(this.filter), toObservable(this.page), toObservable(this.reload)])
      .pipe(
        switchMap(([type, page]) => this.api.reports(type, page, PAGE_SIZE).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((p) => this.history.set(p));
  }

  protected generate(): void {
    if (this.customInvalid()) return;
    this.generating.set(true);
    const custom = this.preset() === 'CUSTOM';
    this.api
      .generateReport({
        type: this.type(),
        period: this.preset(),
        from: custom ? this.from() : undefined,
        to: custom ? this.to() : undefined,
      })
      .subscribe({
        next: (report) => {
          this.generating.set(false);
          this.toast.success('Relatório emitido.');
          void this.router.navigate(['/reports', report.id]);
        },
        error: () => this.generating.set(false),
      });
  }

  protected setFilter(value: string): void {
    this.filter.set(value as ReportType | '');
    this.page.set(0);
  }

  protected remove(report: ReportSummary): void {
    if (!confirm(`Excluir o relatório "${report.title}" (${report.periodLabel})?`)) return;
    this.api.deleteReport(report.id).subscribe(() => {
      this.toast.success('Relatório excluído.');
      this.reload.update((n) => n + 1);
    });
  }
}

/** Data local (yyyy-MM-dd) deslocada em dias a partir de hoje. */
function isoDate(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
