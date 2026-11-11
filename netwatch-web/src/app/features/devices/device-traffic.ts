import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, of, switchMap } from 'rxjs';

import { Api } from '../../core/api';
import { endpoint, formatBytes, timeAgo } from '../../core/format';
import { DeviceTraffic, TrafficWindow } from '../../core/models';
import { Icon } from '../../shared/icon';

/** Painel "Tráfego" do dispositivo: volume, com quem conversa e fluxos recentes. */
@Component({
  selector: 'app-device-traffic',
  imports: [Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './device-traffic.html',
  styles: `
    .totals { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .total { background: var(--surface-2); border-radius: var(--radius-sm); padding: 10px 12px; }
    .total .label { font-size: 12px; color: var(--text-muted); }
    .total .value { font-size: 18px; color: var(--text-primary); }
    .peers { list-style: none; margin: 0 0 16px; padding: 0; display: flex; flex-direction: column; gap: 6px; }
    .peers li { display: flex; gap: 10px; align-items: baseline; }
    .peers .who { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    h3 { font-size: 13px; color: var(--text-secondary); margin: 0 0 8px; font-weight: 600; }
    .link { color: var(--accent); }
  `,
})
export class DeviceTrafficPanel {
  private readonly api = inject(Api);

  readonly deviceId = input.required<string>();

  protected readonly window = signal<TrafficWindow>('24h');
  protected readonly data = signal<DeviceTraffic | null>(null);
  protected readonly loaded = signal(false);

  protected readonly formatBytes = formatBytes;
  protected readonly timeAgo = timeAgo;
  protected readonly endpoint = endpoint;

  protected readonly windows: { value: TrafficWindow; label: string }[] = [
    { value: '1h', label: '1 h' },
    { value: '24h', label: '24 h' },
    { value: '7d', label: '7 dias' },
  ];

  protected readonly hasTraffic = computed(() => {
    const d = this.data();
    return !!d && (d.bytesSent + d.bytesReceived > 0 || d.flows > 0);
  });

  /** Sem volume medido (tabela de conexões), mostra contagem de fluxos no lugar de bytes. */
  protected readonly hasVolume = computed(() => {
    const d = this.data();
    return !!d && d.bytesSent + d.bytesReceived > 0;
  });

  constructor() {
    combineLatest([toObservable(this.deviceId), toObservable(this.window)])
      .pipe(
        switchMap(([id, window]) => this.api.deviceTraffic(id, window).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((d) => {
        this.data.set(d);
        this.loaded.set(true);
      });
  }
}
