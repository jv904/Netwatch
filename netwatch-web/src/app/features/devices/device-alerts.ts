import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';

import { Api } from '../../core/api';
import { Page, SecurityAlert } from '../../core/models';
import { AlertList } from '../../shared/alert-list';
import { Icon } from '../../shared/icon';

/** Painel "Alertas" do dispositivo: alertas ativos gerados pelo motor de segurança. */
@Component({
  selector: 'app-device-alerts',
  imports: [AlertList, Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <div class="card-header">
        <h2><app-icon name="bell" [size]="16" /> Alertas ativos</h2>
        @if (data(); as p) {
          <span class="hint">{{ p.totalElements }}</span>
        }
        <span class="spacer"></span>
        <a class="btn btn-sm" routerLink="/alerts" [queryParams]="{ tab: 'all', q: ip() }">Ver todos</a>
      </div>
      @if (data(); as p) {
        <app-alert-list
          [alerts]="p.content"
          [showRecommendation]="false"
          [clickable]="true"
          (open)="openAlert($event)"
          emptyText="Nenhum alerta ativo para este dispositivo."
          ariaLabel="Alertas do dispositivo"
        />
      } @else {
        <div class="skeleton" style="height: 100px"></div>
      }
    </section>
  `,
})
export class DeviceAlertsPanel {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  readonly deviceId = input.required<string>();
  /** IP do dispositivo, usado no link "Ver todos" (inclui alertas antigos). */
  readonly ip = input('');

  protected readonly data = signal<Page<SecurityAlert> | null>(null);

  constructor() {
    toObservable(this.deviceId)
      .pipe(
        switchMap((id) => this.api.securityAlerts({ deviceId: id, state: 'active', size: 5 }).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((p) => this.data.set(p));
  }

  protected openAlert(alert: SecurityAlert): void {
    void this.router.navigate(['/alerts'], { queryParams: { id: alert.id, tab: 'all' } });
  }
}
