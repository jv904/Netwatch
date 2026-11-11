import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map, switchMap } from 'rxjs';

import { Api } from '../../core/api';
import { byteScale, formatDateTime } from '../../core/format';
import { ReportChart, ReportView } from '../../core/models';
import { AreaChart, AreaPoint } from '../../shared/area-chart';
import { BarChart, BarItem } from '../../shared/bar-chart';
import { Icon } from '../../shared/icon';

/** Linhas exibidas de cada tabela antes de "Mostrar todas". */
const VISIBLE_ROWS = 25;

@Component({
  selector: 'app-report-view',
  imports: [AreaChart, BarChart, Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './report-view.html',
  styleUrl: './report-view.scss',
})
export class ReportViewPage {
  private readonly api = inject(Api);

  protected readonly view = signal<ReportView | null>(null);
  protected readonly notFound = signal(false);
  /** Tabelas abertas por completo (índice global). */
  protected readonly expanded = signal<Set<number>>(new Set());

  protected readonly formatDateTime = formatDateTime;
  protected readonly visibleRows = VISIBLE_ROWS;

  /** Índice global de cada tabela (seção, tabela) — o mesmo usado no CSV de uma tabela. */
  protected readonly tableIndex = computed(() => {
    const index = new Map<string, number>();
    let i = 0;
    this.view()?.document.sections.forEach((s, si) => s.tables.forEach((_, ti) => index.set(`${si}:${ti}`, i++)));
    return index;
  });

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(
        map((p) => p.get('id') ?? ''),
        switchMap((id) => this.api.report(id)),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (v) => this.view.set(v),
        error: () => this.notFound.set(true),
      });
  }

  protected pdfUrl(): string {
    return this.api.reportPdfUrl(this.view()!.report.id);
  }

  protected csvUrl(table?: number): string {
    return this.api.reportCsvUrl(this.view()!.report.id, table);
  }

  protected print(): void {
    window.print();
  }

  protected toggle(index: number): void {
    this.expanded.update((set) => {
      const next = new Set(set);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  }

  protected bars(chart: ReportChart): { items: BarItem[]; unit: string } {
    const { unit, divisor } = this.scale(chart);
    return {
      unit,
      items: chart.points.map((p) => ({ label: p.label, value: Math.round((p.value / divisor) * 10) / 10 })),
    };
  }

  protected line(chart: ReportChart): { points: AreaPoint[]; unit: string } {
    const { unit, divisor } = this.scale(chart);
    return {
      unit,
      points: chart.points.map((p) => ({ label: p.label, value: Math.round((p.value / divisor) * 10) / 10 })),
    };
  }

  private scale(chart: ReportChart): { unit: string; divisor: number } {
    if (chart.unit === 'bytes') {
      return byteScale(Math.max(0, ...chart.points.map((p) => p.value)));
    }
    return { unit: chart.unit ?? '', divisor: 1 };
  }
}
