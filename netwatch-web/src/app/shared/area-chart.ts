import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';

export interface AreaPoint {
  /** Rótulo curto do eixo X (ex.: "09/11 14:30"). */
  label: string;
  value: number;
  /** Linha extra na dica (ex.: "12 novos"). */
  detail?: string;
}

const PAD = { top: 14, right: 14, bottom: 26, left: 40 };

/**
 * Gráfico de área de uma série (sem legenda: o título do card nomeia a série).
 * Linha de 2px, preenchimento a ~10%, grade discreta, mira + dica ao passar o mouse
 * ou navegar com as setas, e uma tabela equivalente para leitores de tela.
 */
@Component({
  selector: 'app-area-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chart" [style.height.px]="height()">
      @if (points().length === 0) {
        <div class="empty">{{ emptyText() }}</div>
      } @else {
        <svg
          [attr.width]="width()"
          [attr.height]="height()"
          role="img"
          [attr.aria-label]="ariaLabel()"
          tabindex="0"
          (pointermove)="onPointer($event)"
          (pointerleave)="active.set(null)"
          (keydown)="onKey($event)"
          (blur)="active.set(null)"
        >
          @for (t of geometry().ticks; track t.value) {
            <line class="grid" [attr.x1]="pad.left" [attr.x2]="width() - pad.right" [attr.y1]="t.y" [attr.y2]="t.y" />
            <text class="tick" [attr.x]="pad.left - 8" [attr.y]="t.y + 4" text-anchor="end">{{ t.text }}</text>
          }
          <path class="area" [attr.d]="geometry().area" />
          <path class="line" [attr.d]="geometry().line" />
          @for (l of geometry().xLabels; track l.i) {
            <text class="tick" [attr.x]="l.x" [attr.y]="height() - 6" [attr.text-anchor]="l.anchor">{{ l.text }}</text>
          }
          @if (geometry().last; as last) {
            <circle class="dot" [attr.cx]="last.x" [attr.cy]="last.y" r="4" />
          }
          @if (hover(); as h) {
            <line class="crosshair" [attr.x1]="h.x" [attr.x2]="h.x" [attr.y1]="pad.top" [attr.y2]="height() - pad.bottom" />
            <circle class="dot" [attr.cx]="h.x" [attr.cy]="h.y" r="5" />
          }
        </svg>
        @if (hover(); as h) {
          <div class="tooltip" [style.left.px]="h.x" [style.top.px]="h.y" [class.flip]="h.x > width() * 0.65">
            <div class="tt-label">{{ h.point.label }}</div>
            <div class="tt-value">{{ h.point.value.toLocaleString('pt-BR') }} {{ unit() }}</div>
            @if (h.point.detail) {
              <div class="tt-detail">{{ h.point.detail }}</div>
            }
          </div>
        }
        <table class="sr-only">
          <caption>{{ ariaLabel() }}</caption>
          <tbody>
            @for (p of points(); track $index) {
              <tr><th scope="row">{{ p.label }}</th><td>{{ p.value }} {{ unit() }}</td></tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .chart { position: relative; width: 100%; }
    svg { display: block; overflow: visible; outline: none; touch-action: none; }
    svg:focus-visible { outline: 2px solid var(--accent); outline-offset: 4px; border-radius: 4px; }
    .grid { stroke: var(--grid); stroke-width: 1; }
    .tick { fill: var(--text-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
    .area { fill: var(--series-1); opacity: 0.1; }
    .line { fill: none; stroke: var(--series-1); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .dot { fill: var(--series-1); stroke: var(--surface-1); stroke-width: 2; }
    .crosshair { stroke: var(--axis); stroke-width: 1; }
    .tooltip {
      position: absolute; transform: translate(12px, -50%); pointer-events: none; z-index: 2;
      background: var(--surface-3); border: 1px solid var(--border-strong); border-radius: 6px;
      padding: 8px 10px; font-size: 12px; white-space: nowrap; box-shadow: 0 6px 20px rgba(0,0,0,.35);
    }
    .tooltip.flip { transform: translate(calc(-100% - 12px), -50%); }
    .tt-label { color: var(--text-muted); }
    .tt-value { font-weight: 600; font-size: 13px; color: var(--text-primary); }
    .tt-detail { color: var(--text-secondary); }
    .empty { height: 100%; display: grid; place-items: center; color: var(--text-muted); text-align: center; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `,
})
export class AreaChart {
  readonly points = input.required<AreaPoint[]>();
  readonly height = input(220);
  readonly unit = input('');
  readonly ariaLabel = input('Gráfico');
  readonly emptyText = input('Sem dados ainda.');

  protected readonly pad = PAD;
  protected readonly width = signal(600);
  protected readonly active = signal<number | null>(null);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const observer = new ResizeObserver(([entry]) => {
        const w = Math.floor(entry.contentRect.width);
        if (w > 0) this.width.set(w);
      });
      observer.observe(this.host.nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  protected readonly geometry = computed(() => {
    const pts = this.points();
    const w = this.width();
    const h = this.height();
    const innerW = Math.max(1, w - PAD.left - PAD.right);
    const innerH = Math.max(1, h - PAD.top - PAD.bottom);
    const yMax = niceMax(Math.max(1, ...pts.map((p) => p.value)));
    const step = yMax / 4;

    const xs = pts.map((_, i) => (pts.length === 1 ? PAD.left + innerW / 2 : PAD.left + (i * innerW) / (pts.length - 1)));
    const ys = pts.map((p) => PAD.top + innerH - (p.value / yMax) * innerH);
    const baseline = PAD.top + innerH;

    const line = xs.map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${ys[i].toFixed(1)}`).join(' ');
    const area = pts.length
      ? `${line} L${xs[xs.length - 1].toFixed(1)},${baseline} L${xs[0].toFixed(1)},${baseline} Z`
      : '';

    const ticks = [0, 1, 2, 3, 4].map((k) => ({
      value: k * step,
      y: PAD.top + innerH - (k * step * innerH) / yMax,
      text: formatTick(k * step),
    }));

    const labelIdx = pts.length <= 1 ? [0] : pts.length <= 4 ? pts.map((_, i) => i) : [0, Math.floor((pts.length - 1) / 2), pts.length - 1];
    const xLabels = [...new Set(labelIdx)].map((i) => ({
      i,
      x: xs[i],
      text: pts[i]?.label ?? '',
      anchor: pts.length === 1 ? 'middle' : i === 0 ? 'start' : i === pts.length - 1 ? 'end' : 'middle',
    }));

    const lastIdx = pts.length - 1;
    return {
      xs,
      ys,
      line,
      area,
      ticks,
      xLabels,
      last: lastIdx >= 0 ? { x: xs[lastIdx], y: ys[lastIdx] } : null,
    };
  });

  protected readonly hover = computed(() => {
    const i = this.active();
    const g = this.geometry();
    if (i === null || i >= this.points().length) return null;
    return { x: g.xs[i], y: g.ys[i], point: this.points()[i] };
  });

  protected onPointer(event: PointerEvent): void {
    const xs = this.geometry().xs;
    if (!xs.length) return;
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    const x = event.clientX - rect.left;
    let best = 0;
    for (let i = 1; i < xs.length; i++) {
      if (Math.abs(xs[i] - x) < Math.abs(xs[best] - x)) best = i;
    }
    this.active.set(best);
  }

  protected onKey(event: KeyboardEvent): void {
    const n = this.points().length;
    if (!n) return;
    const current = this.active() ?? n - 1;
    if (event.key === 'ArrowLeft') this.active.set(Math.max(0, current - 1));
    else if (event.key === 'ArrowRight') this.active.set(Math.min(n - 1, current + 1));
    else if (event.key === 'Escape') this.active.set(null);
    else return;
    event.preventDefault();
  }
}

function niceMax(value: number): number {
  const exp = Math.pow(10, Math.floor(Math.log10(value)));
  const f = value / exp;
  const nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 4 ? 4 : f <= 5 ? 5 : 10;
  return Math.max(4, nice * exp);
}

function formatTick(value: number): string {
  if (value >= 10000) return `${Math.round(value / 1000)}K`;
  return Number.isInteger(value) ? value.toLocaleString('pt-BR') : value.toLocaleString('pt-BR', { maximumFractionDigits: 1 });
}
