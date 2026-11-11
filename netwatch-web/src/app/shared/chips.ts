import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CAPTURE_STATUS_LABEL, PORT_STATE_LABEL, RUN_STATUS_LABEL } from '../core/format';
import { CaptureStatus, Device, PortState, RunStatus } from '../core/models';

/**
 * Situação do dispositivo, como no manual: Ativo / Inativo / Atenção (não reconhecido).
 * A cor sempre acompanha um rótulo.
 */
@Component({
  selector: 'app-device-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="view().tone">{{ view().label }}</span>`,
})
export class DeviceStatusChip {
  readonly device = input.required<Pick<Device, 'status' | 'recognized'>>();

  protected readonly view = computed(() => {
    const d = this.device();
    if (!d.recognized) return { label: d.status === 'ACTIVE' ? 'Atenção' : 'Atenção · inativo', tone: 'warning' };
    return d.status === 'ACTIVE' ? { label: 'Ativo', tone: 'good' } : { label: 'Inativo', tone: '' };
  });
}

@Component({
  selector: 'app-port-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="tone()">{{ label() }}</span>`,
})
export class PortStateChip {
  readonly state = input.required<PortState>();
  protected readonly label = computed(() => PORT_STATE_LABEL[this.state()]);
  protected readonly tone = computed(() => (this.state() === 'OPEN' ? 'info' : ''));
}

@Component({
  selector: 'app-capture-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="tone()">{{ label() }}</span>`,
})
export class CaptureStatusChip {
  readonly status = input.required<CaptureStatus>();
  protected readonly label = computed(() => CAPTURE_STATUS_LABEL[this.status()]);
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case 'COMPLETED':
        return 'good';
      case 'STOPPED':
        return 'neutral';
      case 'FAILED':
        return 'critical';
      default:
        return 'info';
    }
  });
}

@Component({
  selector: 'app-run-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="tone()">{{ label() }}</span>`,
})
export class RunStatusChip {
  readonly status = input.required<RunStatus>();
  protected readonly label = computed(() => RUN_STATUS_LABEL[this.status()]);
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case 'COMPLETED':
        return 'good';
      case 'FAILED':
        return 'critical';
      default:
        return 'info';
    }
  });
}
