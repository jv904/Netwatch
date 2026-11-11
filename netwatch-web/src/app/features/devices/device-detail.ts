import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Api } from '../../core/api';
import { DEVICE_TYPES, formatDateTime, timeAgo } from '../../core/format';
import { Device, DeviceType } from '../../core/models';
import { ToastService } from '../../core/toast';
import { DeviceStatusChip } from '../../shared/chips';
import { Icon } from '../../shared/icon';
import { DevicePortsPanel } from './device-ports';
import { DeviceTrafficPanel } from './device-traffic';

@Component({
  selector: 'app-device-detail',
  imports: [RouterLink, DeviceStatusChip, Icon, DevicePortsPanel, DeviceTrafficPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './device-detail.html',
  styleUrl: './device-detail.scss',
})
export class DeviceDetail {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Vem da rota /devices/:id. */
  readonly id = input.required<string>();

  protected readonly device = signal<Device | null>(null);
  protected readonly notFound = signal(false);
  protected readonly saving = signal(false);

  protected readonly name = signal('');
  protected readonly type = signal<DeviceType>('UNKNOWN');
  protected readonly notes = signal('');

  protected readonly types = DEVICE_TYPES;
  protected readonly formatDateTime = formatDateTime;
  protected readonly timeAgo = timeAgo;

  protected readonly dirty = computed(() => {
    const d = this.device();
    if (!d) return false;
    return this.name().trim() !== (d.customName ?? '') || this.type() !== d.type || this.notes().trim() !== (d.notes ?? '');
  });

  constructor() {
    effect(() => {
      const id = this.id();
      this.device.set(null);
      this.notFound.set(false);
      this.api.device(id).subscribe({
        next: (d) => this.setDevice(d),
        error: () => this.notFound.set(true),
      });
    });
  }

  protected save(): void {
    const d = this.device();
    if (!d) return;
    this.saving.set(true);
    this.api
      .updateDevice(d.id, {
        customName: this.name().trim() || null,
        type: this.type(),
        recognized: d.recognized,
        notes: this.notes().trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.setDevice(updated);
          this.toast.success('Alterações salvas.');
        },
        error: () => this.saving.set(false),
      });
  }

  protected reset(): void {
    const d = this.device();
    if (d) this.setDevice(d);
  }

  protected toggleRecognized(): void {
    const d = this.device();
    if (!d) return;
    this.api.setRecognized(d.id, !d.recognized).subscribe((updated) => {
      this.device.set(updated);
      this.toast.success(updated.recognized ? 'Dispositivo marcado como conhecido.' : 'Dispositivo marcado como desconhecido.');
    });
  }

  protected remove(): void {
    const d = this.device();
    if (!d) return;
    const ok = confirm(
      `Excluir ${d.displayName} (${d.ipAddress}) do inventário?\n\nO histórico de portas também será removido. ` +
        'Se o equipamento continuar na rede, ele aparecerá novamente como desconhecido na próxima descoberta.',
    );
    if (!ok) return;
    this.api.deleteDevice(d.id).subscribe(() => {
      this.toast.success('Dispositivo excluído.');
      this.router.navigate(['/devices']);
    });
  }

  private setDevice(d: Device): void {
    this.device.set(d);
    this.name.set(d.customName ?? '');
    this.type.set(d.type);
    this.notes.set(d.notes ?? '');
  }
}
