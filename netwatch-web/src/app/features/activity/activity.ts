import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, switchMap, timer } from 'rxjs';
import { inject } from '@angular/core';

import { Api } from '../../core/api';
import { EVENT_CATEGORY_LABEL, EVENT_SEVERITY_LABEL, EVENT_TYPE_LABEL } from '../../core/format';
import {
  ActivitySummary,
  ActivityWindow,
  EventCategory,
  EventSeverity,
  EventType,
  NetworkEvent,
  Page,
} from '../../core/models';
import { AreaChart, AreaPoint } from '../../shared/area-chart';
import { BarChart, BarItem } from '../../shared/bar-chart';
import { EventList } from '../../shared/event-list';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';
import { Paginator } from '../../shared/paginator';

const PAGE_SIZE = 25;
/** Atualização automática da tela (os eventos chegam conforme descobertas, verificações e capturas rodam). */
const REFRESH_MS = 30_000;

@Component({
  selector: 'app-activity',
  imports: [AreaChart, BarChart, EventList, Icon, KpiCard, Paginator, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './activity.html',
  styleUrl: './activity.scss',
})
export class Activity {
  private readonly api = inject(Api);

  protected readonly window = signal<ActivityWindow>('7d');
  protected readonly summary = signal<ActivitySummary | null>(null);
  protected readonly loadError = signal(false);

  protected readonly category = signal<EventCategory | ''>('');
  protected readonly severity = signal<EventSeverity | ''>('');
  protected readonly type = signal<EventType | ''>('');
  protected readonly search = signal('');
  protected readonly page = signal(0);
  protected readonly events = signal<Page<NetworkEvent> | null>(null);

  protected readonly typeLabel = EVENT_TYPE_LABEL;
  protected readonly severityLabel = EVENT_SEVERITY_LABEL;

  protected readonly windows: { value: ActivityWindow; label: string }[] = [
    { value: '24h', label: '24 horas' },
    { value: '7d', label: '7 dias' },
    { value: '30d', label: '30 dias' },
  ];
  protected readonly categories: { value: EventCategory | ''; label: string }[] = [
    { value: '', label: 'Todas' },
    ...(Object.keys(EVENT_CATEGORY_LABEL) as EventCategory[]).map((c) => ({ value: c, label: EVENT_CATEGORY_LABEL[c] })),
  ];
  protected readonly severities: EventSeverity[] = ['WARNING', 'NOTICE', 'INFO'];

  protected readonly timeline = computed<AreaPoint[]>(() => {
    const s = this.summary();
    if (!s || s.totals.total === 0) return [];
    const daily = s.bucketSeconds >= 86400;
    return s.series.map((p) => {
      const d = new Date(p.at);
      const label = daily
        ? d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
        : d.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
      return {
        label,
        value: p.total,
        detail: p.warnings ? `${p.warnings} importantes` : undefined,
      };
    });
  });

  protected readonly typeBars = computed<BarItem[]>(() =>
    (this.summary()?.byType ?? []).map((t) => ({
      label: EVENT_TYPE_LABEL[t.type],
      value: t.count,
      hint: `${EVENT_SEVERITY_LABEL[t.severity]} — clique para filtrar`,
      type: t.type,
    })),
  );

  /** Distribuição por hora do dia, com a altura relativa ao horário mais movimentado. */
  protected readonly hours = computed(() => {
    const list = this.summary()?.byHour ?? [];
    const max = Math.max(1, ...list.map((h) => h.count));
    return list.map((h) => ({ ...h, height: Math.round((h.count / max) * 100), label: `${String(h.hour).padStart(2, '0')}h` }));
  });

  protected readonly busiestHour = computed(() => {
    const list = this.summary()?.byHour ?? [];
    const top = list.reduce((best, h) => (h.count > best.count ? h : best), { hour: -1, count: 0 });
    return top.count > 0 ? `${String(top.hour).padStart(2, '0')}h` : null;
  });

  protected readonly hasFilters = computed(() => !!(this.category() || this.severity() || this.type() || this.search()));

  constructor() {
    combineLatest([toObservable(this.window), timer(0, REFRESH_MS)])
      .pipe(
        switchMap(([w]) => this.api.eventSummary(w).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((s) => {
        this.loadError.set(s === null);
        if (s) this.summary.set(s);
      });

    combineLatest([
      toObservable(this.window),
      toObservable(this.category),
      toObservable(this.severity),
      toObservable(this.type),
      toObservable(this.search).pipe(debounceTime(300)),
      toObservable(this.page),
      timer(0, REFRESH_MS),
    ])
      .pipe(
        switchMap(([window, category, severity, type, q, page]) =>
          this.api
            .events({ window, category, severity, type, q, page, size: PAGE_SIZE })
            .pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((p) => {
        if (p) this.events.set(p);
      });
  }

  protected setWindow(w: ActivityWindow): void {
    this.window.set(w);
    this.page.set(0);
  }

  protected setCategory(c: EventCategory | ''): void {
    this.category.set(c);
    this.type.set('');
    this.page.set(0);
  }

  protected setSeverity(value: string): void {
    this.severity.set(value as EventSeverity | '');
    this.page.set(0);
  }

  protected setSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
  }

  protected filterByType(item: BarItem): void {
    const type = item.type as EventType | undefined;
    if (type) {
      this.category.set('');
      this.type.set(type);
      this.page.set(0);
      document.getElementById('eventos')?.scrollIntoView({ behavior: 'smooth' });
    }
  }

  protected clearFilters(): void {
    this.category.set('');
    this.severity.set('');
    this.type.set('');
    this.search.set('');
    this.page.set(0);
  }
}
