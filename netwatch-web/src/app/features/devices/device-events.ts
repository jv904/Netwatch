import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, of, switchMap } from 'rxjs';

import { Api } from '../../core/api';
import { NetworkEvent, Page } from '../../core/models';
import { EventList } from '../../shared/event-list';
import { Icon } from '../../shared/icon';
import { Paginator } from '../../shared/paginator';

/** Painel "Histórico" do dispositivo: tudo o que aconteceu com ele. */
@Component({
  selector: 'app-device-events',
  imports: [EventList, Icon, Paginator],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <div class="card-header">
        <h2><app-icon name="clock" [size]="16" /> Histórico</h2>
        @if (data(); as p) {
          <span class="hint">{{ p.totalElements }} eventos</span>
        }
      </div>
      @if (data(); as p) {
        <app-event-list
          [events]="p.content"
          [showDevice]="false"
          emptyText="Nenhum evento registrado para este dispositivo ainda."
          ariaLabel="Histórico do dispositivo"
        />
        @if (p.totalElements > p.size) {
          <app-paginator [page]="p.page" [size]="p.size" [total]="p.totalElements" (pageChange)="page.set($event)" />
        }
      } @else {
        <div class="skeleton" style="height: 120px"></div>
      }
    </section>
  `,
})
export class DeviceEventsPanel {
  private readonly api = inject(Api);

  readonly deviceId = input.required<string>();

  protected readonly page = signal(0);
  protected readonly data = signal<Page<NetworkEvent> | null>(null);

  constructor() {
    combineLatest([toObservable(this.deviceId), toObservable(this.page)])
      .pipe(
        switchMap(([id, page]) => this.api.deviceEvents(id, page, 10).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((p) => this.data.set(p));
  }
}
