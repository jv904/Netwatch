import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';

import { Api } from '../../core/api';
import { ALERT_SEVERITY_LABEL } from '../../core/format';
import { AlertSeverity, SecurityRule } from '../../core/models';
import { ToastService } from '../../core/toast';

/** Uma regra do motor: liga/desliga, gravidade e parâmetros, com salvar e restaurar o padrão. */
@Component({
  selector: 'app-rule-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = rule();
    <article class="rule" [class.off]="!enabled()">
      <header>
        <label class="switch" [title]="enabled() ? 'Desligar regra' : 'Ligar regra'">
          <input type="checkbox" [checked]="enabled()" (change)="enabled.set($any($event.target).checked)" [attr.aria-label]="'Regra ' + r.title" />
          <span class="track"><span class="thumb"></span></span>
        </label>
        <div class="titles">
          <h3>{{ r.title }}</h3>
          <p>{{ r.description }}</p>
        </div>
        @if (r.activeAlerts > 0) {
          <span class="chip warning nums" title="Alertas ativos gerados por esta regra">{{ r.activeAlerts }} ativos</span>
        }
      </header>

      <div class="fields">
        <label class="field">
          <span>Gravidade</span>
          <select class="select" [value]="severity()" (change)="severity.set($any($event.target).value)">
            @for (s of severities; track s) {
              <option [value]="s" [selected]="s === severity()">{{ severityLabel[s] }}{{ s === r.defaultSeverity ? ' (padrão)' : '' }}</option>
            }
          </select>
        </label>
        @for (p of r.params; track p.name) {
          <label class="field" [class.wide]="p.type === 'PORT_LIST' || p.type === 'IP_LIST'">
            <span [title]="p.description">{{ p.label }}</span>
            <input
              class="input"
              [class.mono]="p.type !== 'HOUR' && p.type !== 'INTEGER'"
              [type]="p.type === 'HOUR' || p.type === 'INTEGER' ? 'number' : 'text'"
              [attr.min]="p.type === 'HOUR' ? 0 : p.type === 'INTEGER' ? 1 : null"
              [attr.max]="p.type === 'HOUR' ? 23 : null"
              [placeholder]="p.defaultValue || 'vazio'"
              [value]="values()[p.name] ?? ''"
              (input)="setValue(p.name, $any($event.target).value)"
            />
            <small>{{ p.description }}</small>
          </label>
        }
      </div>

      <footer>
        <span class="muted small">{{ r.recommendation }}</span>
        <span class="spacer"></span>
        <button class="btn btn-ghost btn-sm" type="button" (click)="restoreDefaults()" [disabled]="isDefault()">Restaurar padrão</button>
        <button class="btn btn-primary btn-sm" type="button" (click)="save()" [disabled]="!dirty() || saving()">
          @if (saving()) { <span class="spinner"></span> }
          Salvar
        </button>
      </footer>
    </article>
  `,
  styles: `
    .rule { border: 1px solid var(--border); border-radius: var(--radius); background: var(--surface-1); padding: 14px 16px; display: flex; flex-direction: column; gap: 12px; }
    .rule.off .titles, .rule.off .fields { opacity: 0.55; }
    header { display: flex; gap: 12px; align-items: flex-start; }
    .titles { flex: 1; min-width: 0; }
    h3 { margin: 0; font-size: 14px; color: var(--text-primary); }
    .titles p { margin: 4px 0 0; color: var(--text-secondary); font-size: 12.5px; }
    .fields { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
    .field.wide { grid-column: 1 / -1; }
    .field small { color: var(--text-muted); font-size: 11.5px; }
    footer { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; border-top: 1px solid var(--border); padding-top: 10px; }
    footer .muted { flex: 1 1 260px; }
    .small { font-size: 12px; }
    .switch { position: relative; display: inline-flex; flex: none; cursor: pointer; margin-top: 2px; }
    .switch input { position: absolute; opacity: 0; width: 100%; height: 100%; margin: 0; cursor: pointer; }
    .track { width: 34px; height: 20px; border-radius: 10px; background: var(--surface-3); border: 1px solid var(--border-strong); position: relative; transition: background 0.15s; }
    .thumb { position: absolute; top: 2px; left: 2px; width: 14px; height: 14px; border-radius: 50%; background: var(--text-muted); transition: transform 0.15s, background 0.15s; }
    .switch input:checked + .track { background: var(--accent-soft); border-color: var(--accent); }
    .switch input:checked + .track .thumb { transform: translateX(14px); background: var(--accent); }
    .switch input:focus-visible + .track { box-shadow: 0 0 0 3px var(--accent-soft); }
  `,
})
export class RuleCard {
  private readonly api = inject(Api);
  private readonly toast = inject(ToastService);

  readonly rule = input.required<SecurityRule>();
  readonly saved = output<SecurityRule>();

  protected readonly enabled = signal(true);
  protected readonly severity = signal<AlertSeverity>('MEDIUM');
  protected readonly values = signal<Record<string, string>>({});
  protected readonly saving = signal(false);

  protected readonly severities: AlertSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  protected readonly severityLabel = ALERT_SEVERITY_LABEL;

  protected readonly dirty = computed(() => {
    const r = this.rule();
    return (
      this.enabled() !== r.enabled ||
      this.severity() !== r.severity ||
      r.params.some((p) => (this.values()[p.name] ?? '').trim() !== p.value)
    );
  });

  protected readonly isDefault = computed(() => {
    const r = this.rule();
    return this.severity() === r.defaultSeverity && r.params.every((p) => (this.values()[p.name] ?? '').trim() === p.defaultValue);
  });

  constructor() {
    // Recarrega o formulário sempre que a regra vier do servidor (inclusive depois de salvar).
    effect(() => {
      const r = this.rule();
      this.enabled.set(r.enabled);
      this.severity.set(r.severity);
      this.values.set(Object.fromEntries(r.params.map((p) => [p.name, p.value])));
    });
  }

  protected setValue(name: string, value: string): void {
    this.values.update((v) => ({ ...v, [name]: value }));
  }

  protected restoreDefaults(): void {
    const r = this.rule();
    this.severity.set(r.defaultSeverity);
    this.values.set(Object.fromEntries(r.params.map((p) => [p.name, p.defaultValue])));
  }

  protected save(): void {
    const r = this.rule();
    this.saving.set(true);
    const params = Object.fromEntries(r.params.map((p) => [p.name, (this.values()[p.name] ?? '').trim()]));
    this.api.updateSecurityRule(r.code, { enabled: this.enabled(), severity: this.severity(), params }).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.toast.success(`Regra "${updated.title}" salva.`);
        this.saved.emit(updated);
      },
      error: () => this.saving.set(false),
    });
  }
}
