import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, of, switchMap } from 'rxjs';

import { Api } from '../../core/api';
import { formatDateTime, timeAgo } from '../../core/format';
import { ExposedService, NetworkPort, Page, PortState } from '../../core/models';
import { BarChart, BarItem } from '../../shared/bar-chart';
import { PortStateChip } from '../../shared/chips';
import { Icon } from '../../shared/icon';
import { Paginator } from '../../shared/paginator';

const PAGE_SIZE = 50;

@Component({
  selector: 'app-ports',
  imports: [BarChart, PortStateChip, Icon, Paginator, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ports.html',
})
export class Ports {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  protected readonly state = signal<PortState | ''>('OPEN');
  protected readonly port = signal<number | null>(null);
  protected readonly search = signal('');
  protected readonly page = signal(0);
  protected readonly result = signal<Page<NetworkPort> | null>(null);
  protected readonly services = signal<ExposedService[] | null>(null);

  protected readonly timeAgo = timeAgo;
  protected readonly formatDateTime = formatDateTime;

  protected readonly stateOptions: { value: PortState | ''; label: string }[] = [
    { value: 'OPEN', label: 'Abertas' },
    { value: '', label: 'Todas' },
    { value: 'CLOSED', label: 'Fechadas' },
    { value: 'FILTERED', label: 'Sem resposta' },
  ];

  protected readonly serviceBars = computed<BarItem[]>(() =>
    (this.services() ?? []).slice(0, 10).map((s) => ({
      label: `${s.port} · ${s.service ?? 'Não identificado'}`,
      value: s.devices,
      hint: 'clique para ver os dispositivos',
      port: s.port,
    })),
  );

  protected readonly totalOpen = computed(() => (this.services() ?? []).reduce((sum, s) => sum + s.devices, 0));

  constructor() {
    this.api.exposedServices().pipe(catchError(() => of([]))).subscribe((s) => this.services.set(s));

    combineLatest([
      toObservable(this.state),
      toObservable(this.port),
      toObservable(this.search).pipe(debounceTime(300)),
      toObservable(this.page),
    ])
      .pipe(
        debounceTime(0),
        switchMap(([state, port, q, page]) =>
          this.api.ports({ state, port, q, page, size: PAGE_SIZE }).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((r) => this.result.set(r));
  }

  protected setState(value: PortState | ''): void {
    this.state.set(value);
    this.page.set(0);
  }

  protected setSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
  }

  /** Clique numa barra: mostra os dispositivos com aquela porta aberta. */
  protected filterByService(item: BarItem): void {
    this.state.set('OPEN');
    this.port.set(item.port ?? null);
    this.page.set(0);
    document.getElementById('tabela')?.scrollIntoView({ behavior: 'smooth' });
  }

  protected clearPort(): void {
    this.port.set(null);
    this.page.set(0);
  }

  protected open(row: NetworkPort): void {
    this.router.navigate(['/devices', row.deviceId]);
  }
}
