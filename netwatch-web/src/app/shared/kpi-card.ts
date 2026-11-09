import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Icon, IconName } from './icon';

/** Indicador do Dashboard: rótulo, valor e uma linha de contexto. */
@Component({
  selector: 'app-kpi-card',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kpi card" [class.loading]="value() === null">
      <div class="kpi-head">
        <span class="kpi-label">{{ label() }}</span>
        <span class="kpi-icon" [class]="'tone-' + tone()"><app-icon [name]="icon()" [size]="16" /></span>
      </div>
      <div class="kpi-value" [class.skeleton]="value() === null">{{ value() ?? '000' }}</div>
      <div class="kpi-foot" [class]="'tone-' + tone()">{{ caption() }}</div>
    </div>
  `,
  styles: `
    .kpi { display: flex; flex-direction: column; gap: 6px; }
    .kpi-head { display: flex; align-items: center; justify-content: space-between; }
    .kpi-label { color: var(--text-secondary); font-size: 13px; }
    .kpi-icon { display: inline-flex; color: var(--text-muted); }
    .kpi-value { font-size: 30px; font-weight: 600; letter-spacing: -0.02em; line-height: 1.15; width: fit-content; }
    .kpi-foot { font-size: 12.5px; color: var(--text-muted); min-height: 18px; }
    .tone-warning { color: var(--warning); }
    .tone-good { color: #4fd14f; }
    .tone-info { color: #7fb3f0; }
  `,
})
export class KpiCard {
  readonly label = input.required<string>();
  readonly value = input<string | number | null>(null);
  readonly caption = input('');
  readonly icon = input<IconName>('activity');
  readonly tone = input<'neutral' | 'warning' | 'good' | 'info'>('neutral');
}
