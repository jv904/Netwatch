import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';

import { Api } from '../../core/api';
import { DEVICE_TYPES, deviceTypeLabel, timeAgo } from '../../core/format';
import { Device, DeviceQuery, DeviceStatus, DeviceType, Page } from '../../core/models';
import { DeviceStatusChip } from '../../shared/chips';
import { Icon } from '../../shared/icon';
import { Paginator } from '../../shared/paginator';

interface Column {
  key: string;
  label: string;
  sort?: string;
}

const PAGE_SIZE = 25;

@Component({
  selector: 'app-device-list',
  imports: [RouterLink, DeviceStatusChip, Paginator, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './device-list.html',
})
export class DeviceList {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly types = DEVICE_TYPES;
  protected readonly typeLabel = deviceTypeLabel;
  protected readonly timeAgo = timeAgo;

  protected readonly columns: Column[] = [
    { key: 'name', label: 'Dispositivo', sort: 'hostname' },
    { key: 'ip', label: 'Endereço IP', sort: 'ipAddress' },
    { key: 'vendor', label: 'Fabricante', sort: 'vendor' },
    { key: 'type', label: 'Tipo', sort: 'type' },
    { key: 'status', label: 'Situação', sort: 'status' },
    { key: 'seen', label: 'Última identificação', sort: 'lastSeenAt' },
  ];

  /** Estado atual dos filtros (fonte da verdade: a URL). */
  protected readonly query = signal<Required<Pick<DeviceQuery, 'q' | 'page' | 'sort'>> & DeviceQuery>({
    q: '',
    page: 0,
    sort: 'lastSeenAt,desc',
  });
  protected readonly searchText = signal('');
  protected readonly result = signal<Page<Device> | null>(null);
  protected readonly loading = signal(false);

  protected readonly recognitionValue = computed(() => {
    const r = this.query().recognized;
    return r === undefined || r === null ? '' : String(r);
  });
  protected readonly sortField = computed(() => this.query().sort.split(',')[0]);
  protected readonly sortDesc = computed(() => this.query().sort.endsWith(',desc'));
  protected readonly hasFilters = computed(() => {
    const q = this.query();
    return !!(q.q || q.status || q.type || q.recognized !== undefined);
  });

  private readonly search$ = new Subject<string>();

  constructor() {
    this.route.queryParamMap
      .pipe(
        tap((params) => {
          const q = parseQuery(params);
          this.query.set(q);
          this.searchText.set(q.q);
          this.loading.set(true);
        }),
        switchMap(() => this.api.devices({ ...this.query(), size: PAGE_SIZE }).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.loading.set(false);
        this.result.set(page ?? { content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
      });

    this.search$.pipe(debounceTime(350), takeUntilDestroyed()).subscribe((q) => this.apply({ q, page: 0 }));
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    this.search$.next(value);
  }

  protected setStatus(value: string): void {
    this.apply({ status: (value || undefined) as DeviceStatus | undefined, page: 0 });
  }

  protected setRecognition(value: string): void {
    this.apply({ recognized: value === '' ? undefined : value === 'true', page: 0 });
  }

  protected setType(value: string): void {
    this.apply({ type: (value || undefined) as DeviceType | undefined, page: 0 });
  }

  protected sortBy(column: Column): void {
    if (!column.sort) return;
    const desc = this.sortField() === column.sort ? !this.sortDesc() : column.sort === 'lastSeenAt';
    this.apply({ sort: `${column.sort},${desc ? 'desc' : 'asc'}`, page: 0 });
  }

  protected goToPage(page: number): void {
    this.apply({ page });
  }

  protected clearFilters(): void {
    this.router.navigate([], { queryParams: {} });
  }

  protected open(device: Device): void {
    this.router.navigate(['/devices', device.id]);
  }

  private apply(change: Partial<DeviceQuery>): void {
    const next = { ...this.query(), ...change };
    this.router.navigate([], {
      replaceUrl: true,
      queryParams: {
        q: next.q || null,
        status: next.status || null,
        type: next.type || null,
        recognized: next.recognized === undefined || next.recognized === null ? null : String(next.recognized),
        page: next.page ? next.page : null,
        sort: next.sort && next.sort !== 'lastSeenAt,desc' ? next.sort : null,
      },
    });
  }
}

function parseQuery(params: ParamMap) {
  const recognized = params.get('recognized');
  return {
    q: params.get('q') ?? '',
    status: (params.get('status') ?? undefined) as DeviceStatus | undefined,
    type: (params.get('type') ?? undefined) as DeviceType | undefined,
    recognized: recognized === null ? undefined : recognized === 'true',
    page: Number(params.get('page') ?? 0) || 0,
    sort: params.get('sort') ?? 'lastSeenAt,desc',
  };
}
