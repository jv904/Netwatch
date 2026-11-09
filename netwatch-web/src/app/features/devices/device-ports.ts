import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { Subscription, catchError, of, switchMap, takeWhile, timer } from 'rxjs';

import { Api } from '../../core/api';
import { formatDateTime, formatDuration, timeAgo } from '../../core/format';
import { DevicePorts, PortScan, PortScanType } from '../../core/models';
import { ToastService } from '../../core/toast';
import { PortStateChip, RunStatusChip } from '../../shared/chips';
import { Icon } from '../../shared/icon';

/** Painel "Portas e serviços" do dispositivo (seção 6.2 do manual). */
@Component({
  selector: 'app-device-ports',
  imports: [PortStateChip, RunStatusChip, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './device-ports.html',
  styleUrl: './device-ports.scss',
})
export class DevicePortsPanel {
  private readonly api = inject(Api);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly deviceId = input.required<string>();

  protected readonly data = signal<DevicePorts | null>(null);
  protected readonly lastDetail = signal<PortScan | null>(null);
  protected readonly mode = signal<PortScanType>('COMMON');
  protected readonly customPorts = signal('');
  protected readonly showAll = signal(false);
  protected readonly starting = signal(false);

  protected readonly timeAgo = timeAgo;
  protected readonly formatDateTime = formatDateTime;
  protected readonly formatDuration = formatDuration;

  private polling?: Subscription;

  protected readonly scanning = computed(() => this.data()?.scanning ?? false);
  protected readonly newlyOpened = computed(
    () => new Set((this.lastDetail()?.results ?? []).filter((r) => r.newlyOpened).map((r) => r.port)),
  );
  protected readonly visiblePorts = computed(() => {
    const ports = this.data()?.ports ?? [];
    return this.showAll() ? ports : ports.filter((p) => p.state === 'OPEN');
  });
  protected readonly openCount = computed(() => (this.data()?.ports ?? []).filter((p) => p.state === 'OPEN').length);

  constructor() {
    effect(() => {
      const id = this.deviceId();
      this.data.set(null);
      this.lastDetail.set(null);
      this.watch(id);
    });
    this.destroyRef.onDestroy(() => this.polling?.unsubscribe());
  }

  protected start(): void {
    const ports = this.customPorts().trim();
    if (this.mode() === 'CUSTOM' && !ports) {
      this.toast.error('Informe as portas a verificar, por exemplo: 22,80,8000-8100.');
      return;
    }
    this.starting.set(true);
    this.api.startPortScan(this.deviceId(), this.mode(), this.mode() === 'CUSTOM' ? ports : undefined).subscribe({
      next: (scan) => {
        this.starting.set(false);
        this.toast.info(`Verificando ${scan.portCount} portas em ${scan.ipAddress}…`);
        this.watch(this.deviceId());
      },
      error: () => this.starting.set(false),
    });
  }

  /** Carrega a situação e acompanha enquanto houver verificação em andamento. */
  private watch(id: string): void {
    this.polling?.unsubscribe();
    let wasScanning = false;
    this.polling = timer(0, 1500)
      .pipe(
        switchMap(() => this.api.devicePorts(id).pipe(catchError(() => of(null)))),
        takeWhile((d) => d?.scanning === true, true),
      )
      .subscribe((d) => {
        if (!d) return;
        this.data.set(d);
        if (d.scanning) {
          wasScanning = true;
        } else {
          if (wasScanning && d.lastScan) {
            this.toast.success(`Verificação concluída: ${d.lastScan.openCount} porta(s) aberta(s).`);
          }
          this.loadLastDetail(d);
        }
      });
  }

  private loadLastDetail(d: DevicePorts): void {
    if (!d.lastScan || d.lastScan.status === 'RUNNING') return;
    this.api
      .portScan(d.lastScan.id)
      .pipe(catchError(() => of(null)))
      .subscribe((scan) => this.lastDetail.set(scan));
  }
}
