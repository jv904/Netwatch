import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap, timer } from 'rxjs';

import { Api } from '../../core/api';
import { formatDateTime, formatDuration, formatNumber, timeAgo } from '../../core/format';
import { DashboardOverview, Device, NetworkEvent, SecurityAlert, SecuritySummary } from '../../core/models';
import { ToastService } from '../../core/toast';
import { AreaChart, AreaPoint } from '../../shared/area-chart';
import { BarChart, BarItem } from '../../shared/bar-chart';
import { RunStatusChip } from '../../shared/chips';
import { AlertList } from '../../shared/alert-list';
import { EventList } from '../../shared/event-list';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';

@Component({
  selector: 'app-dashboard',
  imports: [KpiCard, AreaChart, AlertList, BarChart, EventList, RunStatusChip, Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly overview = signal<DashboardOverview | null>(null);
  protected readonly unknown = signal<Device[] | null>(null);
  protected readonly unknownTotal = signal(0);
  protected readonly recentEvents = signal<NetworkEvent[] | null>(null);
  protected readonly security = signal<SecuritySummary | null>(null);
  protected readonly loadError = signal(false);

  protected readonly timeAgo = timeAgo;
  protected readonly formatDuration = formatDuration;
  protected readonly formatDateTime = formatDateTime;

  constructor() {
    // Atualiza a visão geral a cada 30 s.
    timer(0, 30_000)
      .pipe(
        switchMap(() => this.api.overview().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((o) => {
        this.loadError.set(o === null && this.overview() === null);
        if (o) this.overview.set(o);
      });
    timer(0, 30_000)
      .pipe(
        switchMap(() => this.api.events({ size: 6 }).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        if (page) this.recentEvents.set(page.content);
      });
    timer(0, 30_000)
      .pipe(
        switchMap(() => this.api.securitySummary().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((s) => {
        if (s) this.security.set(s);
      });
    this.loadUnknown();
  }

  protected readonly kpi = computed(() => {
    const o = this.overview();
    if (!o) return null;
    const d = o.devices;
    return {
      total: formatNumber(d.total),
      active: formatNumber(d.active),
      inactiveCaption:
        d.inactive === 0 ? 'Nenhum inativo' : `${formatNumber(d.inactive)} ${d.inactive === 1 ? 'inativo' : 'inativos'}`,
      unknown: formatNumber(d.unrecognized),
      unknownShare: d.total ? Math.round((d.unrecognized / d.total) * 100) : 0,
      openPorts: formatNumber(o.openPorts),
    };
  });

  protected readonly history = computed<AreaPoint[]>(() =>
    (this.overview()?.discoveryHistory ?? []).map((p) => ({
      label: new Date(p.startedAt).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }),
      value: p.devicesFound,
      detail: p.newDevices ? `${p.newDevices} novos` : 'nenhum novo',
    })),
  );

  protected readonly vendors = computed<BarItem[]>(() =>
    (this.overview()?.topVendors ?? []).map((v) => ({
      label: v.vendor ?? 'Não identificado',
      value: v.devices,
    })),
  );

  protected openVendor(item: BarItem): void {
    if (item.label === 'Não identificado') return;
    this.router.navigate(['/devices'], { queryParams: { q: item.label } });
  }

  protected openAlert(alert: SecurityAlert): void {
    void this.router.navigate(['/alerts'], { queryParams: { id: alert.id, tab: 'all' } });
  }

  protected markKnown(device: Device, event: Event): void {
    event.stopPropagation();
    this.api.setRecognized(device.id, true).subscribe(() => {
      this.toast.success(`${device.ipAddress} marcado como conhecido.`);
      this.loadUnknown();
      this.api.overview().subscribe((o) => this.overview.set(o));
    });
  }

  private loadUnknown(): void {
    this.api
      .devices({ recognized: false, size: 6, sort: 'lastSeenAt,desc' })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
        this.unknown.set(page?.content ?? []);
        this.unknownTotal.set(page?.totalElements ?? 0);
      });
  }
}
