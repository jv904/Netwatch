import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export interface BarItem {
  label: string;
  value: number;
  /** Texto complementar exibido na dica. */
  hint?: string;
  /** Dado livre do chamador (ex.: número da porta). */
  port?: number;
}

/**
 * Barras horizontais ordenadas (uma série). Barra de 10px com ponta arredondada,
 * valor na ponta, linha inteira como área de clique/foco e dica nativa.
 */
@Component({
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length === 0) {
      <div class="empty">{{ emptyText() }}</div>
    } @else {
      <ul class="bars" role="list" [attr.aria-label]="ariaLabel()">
        @for (item of items(); track item.label) {
          <li
            class="bar-row"
            [class.interactive]="interactive()"
            [attr.title]="item.label + ': ' + item.value.toLocaleString('pt-BR') + ' ' + unit() + (item.hint ? ' — ' + item.hint : '')"
            [attr.tabindex]="interactive() ? 0 : null"
            (click)="interactive() && itemSelect.emit(item)"
            (keydown.enter)="interactive() && itemSelect.emit(item)"
          >
            <span class="bar-label">{{ item.label }}</span>
            <span class="bar-track">
              <span class="bar" [style.width.%]="(item.value / max()) * 100"></span>
              <span class="bar-value nums">{{ item.value.toLocaleString('pt-BR') }}</span>
            </span>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .bars { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
    .bar-row {
      display: grid; grid-template-columns: minmax(90px, 38%) 1fr; align-items: center; gap: 12px;
      padding: 5px 6px; border-radius: 6px;
    }
    .bar-row:hover, .bar-row:focus-visible { background: var(--surface-2); }
    .bar-row.interactive { cursor: pointer; }
    .bar-label { color: var(--text-secondary); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bar-track { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .bar { height: 10px; min-width: 2px; background: var(--series-1); border-radius: 0 4px 4px 0; }
    .bar-value { color: var(--text-primary); font-size: 12.5px; flex: none; }
    .empty { padding: 28px 0; text-align: center; color: var(--text-muted); }
  `,
})
export class BarChart {
  readonly items = input.required<BarItem[]>();
  readonly unit = input('');
  readonly ariaLabel = input('Gráfico de barras');
  readonly emptyText = input('Sem dados ainda.');
  readonly interactive = input(false);
  readonly itemSelect = output<BarItem>();

  protected readonly max = computed(() => Math.max(1, ...this.items().map((i) => i.value)) * 1.08);
}
