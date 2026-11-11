import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, switchMap, timer } from 'rxjs';

import { AlertNotifier } from '../../core/alert-notifier';
import { Api } from '../../core/api';
import { ALERT_SEVERITY_LABEL } from '../../core/format';
import {
  AlertCounts,
  AlertQuery,
  AlertSeverity,
  AlertStatus,
  BulkAlertAction,
  Page,
  SecurityAlert,
  SecurityRule,
} from '../../core/models';
import { ToastService } from '../../core/toast';
import { AlertList } from '../../shared/alert-list';
import { Icon } from '../../shared/icon';
import { Paginator } from '../../shared/paginator';
import { AlertDetailPanel } from './alert-detail';
import { DismissChoice, DismissDialog } from './dismiss-dialog';

type Tab = 'active' | 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED' | 'all';

const PAGE_SIZE = 20;
const REFRESH_MS = 30_000;

@Component({
  selector: 'app-alerts',
  imports: [AlertDetailPanel, AlertList, DismissDialog, Icon, Paginator],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alerts.html',
  styleUrl: './alerts.scss',
})
export class Alerts {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly notifier = inject(AlertNotifier);

  protected readonly tab = signal<Tab>('active');
  protected readonly severity = signal<AlertSeverity | ''>('');
  protected readonly rule = signal('');
  protected readonly search = signal('');
  protected readonly page = signal(0);
  protected readonly openId = signal<string | null>(null);

  protected readonly result = signal<Page<SecurityAlert> | null>(null);
  protected readonly counts = signal<AlertCounts | null>(null);
  protected readonly rules = signal<SecurityRule[]>([]);
  protected readonly selected = signal<string[]>([]);
  protected readonly busy = signal(false);
  protected readonly bulkDismissing = signal(false);
  private readonly reload = signal(0);

  protected readonly severityLabel = ALERT_SEVERITY_LABEL;
  protected readonly severities: AlertSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

  protected readonly tabs = computed(() => {
    const c = this.counts();
    return [
      { value: 'active' as Tab, label: 'Ativos', count: c?.active },
      { value: 'OPEN' as Tab, label: 'Abertos', count: c?.open },
      { value: 'ACKNOWLEDGED' as Tab, label: 'Reconhecidos', count: c?.acknowledged },
      { value: 'RESOLVED' as Tab, label: 'Resolvidos', count: c?.resolved },
      { value: 'DISMISSED' as Tab, label: 'Descartados', count: c?.dismissed },
      { value: 'all' as Tab, label: 'Todos', count: c ? c.active + c.resolved + c.dismissed : undefined },
    ];
  });

  protected readonly allSelected = computed(() => {
    const list = this.result()?.content ?? [];
    return list.length > 0 && list.every((a) => this.selected().includes(a.id));
  });

  protected readonly hasFilters = computed(() => !!(this.severity() || this.rule() || this.search()));

  constructor() {
    const params = inject(ActivatedRoute).snapshot.queryParamMap;
    const tab = params.get('tab') as Tab | null;
    if (tab && ['active', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED', 'all'].includes(tab)) this.tab.set(tab);
    this.severity.set((params.get('severity') as AlertSeverity | null) ?? '');
    this.rule.set(params.get('rule') ?? '');
    this.search.set(params.get('q') ?? '');
    this.openId.set(params.get('id'));

    this.api
      .securityRules()
      .pipe(catchError(() => of([] as SecurityRule[])))
      .subscribe((r) => this.rules.set(r));

    combineLatest([
      toObservable(this.tab),
      toObservable(this.severity),
      toObservable(this.rule),
      toObservable(this.search).pipe(debounceTime(300)),
      toObservable(this.page),
      toObservable(this.reload),
      timer(0, REFRESH_MS),
    ])
      .pipe(
        switchMap(([tab, severity, rule, q, page]) =>
          combineLatest([
            this.api.securityAlerts({ ...this.tabQuery(tab), severity, rule, q, page, size: PAGE_SIZE }).pipe(catchError(() => of(null))),
            this.api.alertCounts().pipe(catchError(() => of(null))),
          ]),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(([page, counts]) => {
        if (page) {
          this.result.set(page);
          const ids = new Set(page.content.map((a) => a.id));
          this.selected.update((sel) => sel.filter((id) => ids.has(id)));
        }
        if (counts) this.counts.set(counts);
      });
  }

  private tabQuery(tab: Tab): Pick<AlertQuery, 'state' | 'status'> {
    if (tab === 'active') return { state: 'active' };
    if (tab === 'all') return {};
    return { status: tab as AlertStatus };
  }

  protected setTab(tab: Tab): void {
    this.tab.set(tab);
    this.page.set(0);
    this.selected.set([]);
    this.syncUrl();
  }

  protected setSeverity(value: string): void {
    this.severity.set(value as AlertSeverity | '');
    this.page.set(0);
    this.syncUrl();
  }

  protected setRule(value: string): void {
    this.rule.set(value);
    this.page.set(0);
    this.syncUrl();
  }

  protected setSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.syncUrl();
  }

  protected clearFilters(): void {
    this.severity.set('');
    this.rule.set('');
    this.search.set('');
    this.page.set(0);
    this.syncUrl();
  }

  protected open(alert: SecurityAlert): void {
    this.openId.set(alert.id);
    this.syncUrl();
  }

  protected closeDetail(): void {
    this.openId.set(null);
    this.syncUrl();
  }

  protected toggle(id: string): void {
    this.selected.update((sel) => (sel.includes(id) ? sel.filter((s) => s !== id) : [...sel, id]));
  }

  protected toggleAll(): void {
    const list = this.result()?.content ?? [];
    this.selected.set(this.allSelected() ? [] : list.map((a) => a.id));
  }

  protected refresh(): void {
    this.reload.update((n) => n + 1);
    this.notifier.refresh();
  }

  protected bulk(action: BulkAlertAction, choice?: DismissChoice): void {
    const ids = this.selected();
    if (!ids.length) return;
    this.busy.set(true);
    this.api.bulkAlerts(ids, action, choice ?? {}).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.bulkDismissing.set(false);
        this.selected.set([]);
        const verb: Record<BulkAlertAction, string> = {
          ACKNOWLEDGE: 'reconhecidos',
          RESOLVE: 'resolvidos',
          DISMISS: 'descartados',
        };
        const skipped = r.skipped ? '; ' + r.skipped + ' ignorados (a situação não permitia)' : '';
        this.toast.success(`${r.changed} alertas ${verb[action]}${skipped}.`);
        this.refresh();
      },
      error: () => this.busy.set(false),
    });
  }

  protected toggleNotifications(event: Event): void {
    void this.notifier.setBrowserNotifications((event.target as HTMLInputElement).checked);
  }

  private syncUrl(): void {
    void this.router.navigate([], {
      queryParams: {
        tab: this.tab() === 'active' ? null : this.tab(),
        severity: this.severity() || null,
        rule: this.rule() || null,
        q: this.search() || null,
        id: this.openId(),
      },
      replaceUrl: true,
    });
  }
}
