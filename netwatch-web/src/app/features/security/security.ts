import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, of, switchMap, timer } from 'rxjs';

import { Api } from '../../core/api';
import { ALERT_SEVERITY_LABEL, RULE_CATEGORY_LABEL } from '../../core/format';
import { AlertSeverity, Page, RuleCategory, SecurityAlert, SecurityRule, SecuritySummary } from '../../core/models';
import { AlertList } from '../../shared/alert-list';
import { BarChart, BarItem } from '../../shared/bar-chart';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';
import { Paginator } from '../../shared/paginator';
import { RuleCard } from './rule-card';

const REFRESH_MS = 30_000;

@Component({
  selector: 'app-security',
  imports: [AlertList, BarChart, Icon, KpiCard, Paginator, RuleCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './security.html',
  styleUrl: './security.scss',
})
export class Security {
  private readonly api = inject(Api);

  protected readonly summary = signal<SecuritySummary | null>(null);
  protected readonly rules = signal<SecurityRule[] | null>(null);
  protected readonly loadError = signal(false);

  protected readonly severityFilter = signal<AlertSeverity | ''>('');
  protected readonly ruleFilter = signal('');
  protected readonly page = signal(0);
  protected readonly alerts = signal<Page<SecurityAlert> | null>(null);

  protected readonly severityLabel = ALERT_SEVERITY_LABEL;
  protected readonly severities: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

  /** Regras agrupadas por categoria, na ordem do catálogo. */
  protected readonly groups = computed(() => {
    const list = this.rules() ?? [];
    const order: RuleCategory[] = ['DEVICES', 'NETWORK', 'TRAFFIC', 'SCHEDULE'];
    return order
      .map((category) => ({ category, label: RULE_CATEGORY_LABEL[category], rules: list.filter((r) => r.category === category) }))
      .filter((g) => g.rules.length);
  });

  protected readonly ruleBars = computed<BarItem[]>(() =>
    (this.summary()?.byRule ?? [])
      .filter((r) => r.activeAlerts > 0)
      .sort((a, b) => b.activeAlerts - a.activeAlerts)
      .map((r) => ({ label: r.title, value: r.activeAlerts, hint: 'clique para ver os alertas', type: r.rule })),
  );

  constructor() {
    timer(0, REFRESH_MS)
      .pipe(
        switchMap(() => this.api.securitySummary().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((s) => {
        this.loadError.set(s === null && this.summary() === null);
        if (s) this.summary.set(s);
      });
    this.loadRules();

    combineLatest([toObservable(this.severityFilter), toObservable(this.ruleFilter), toObservable(this.page), timer(0, REFRESH_MS)])
      .pipe(
        switchMap(([severity, rule, page]) =>
          this.api.securityAlerts({ severity, rule, page, size: 15 }).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((p) => {
        if (p) this.alerts.set(p);
      });
  }

  protected onSaved(updated: SecurityRule): void {
    this.rules.update((list) => (list ?? []).map((r) => (r.code === updated.code ? updated : r)));
    this.api.securitySummary().subscribe((s) => this.summary.set(s));
  }

  protected filterByRule(item: BarItem): void {
    if (item.type) {
      this.ruleFilter.set(item.type);
      this.page.set(0);
      document.getElementById('alertas')?.scrollIntoView({ behavior: 'smooth' });
    }
  }

  protected setSeverity(value: string): void {
    this.severityFilter.set(value as AlertSeverity | '');
    this.page.set(0);
  }

  protected setRule(value: string): void {
    this.ruleFilter.set(value);
    this.page.set(0);
  }

  private loadRules(): void {
    this.api
      .securityRules()
      .pipe(catchError(() => of(null)))
      .subscribe((r) => this.rules.set(r));
  }
}
