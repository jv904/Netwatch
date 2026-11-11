import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

import { DISMISS_REASON_LABEL } from '../../core/format';
import { DismissReason } from '../../core/models';

export interface DismissChoice {
  reason: DismissReason;
  note: string;
  suppress: boolean;
}

/** Diálogo de descarte: motivo, observação e "não alertar novamente". */
@Component({
  selector: 'app-dismiss-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="overlay" (click)="cancel.emit()" (keydown.escape)="cancel.emit()">
      <div class="dialog card" role="dialog" aria-modal="true" aria-labelledby="dismiss-title" (click)="$event.stopPropagation()">
        <h2 id="dismiss-title">{{ count() > 1 ? 'Descartar ' + count() + ' alertas' : 'Descartar alerta' }}</h2>
        <fieldset>
          <legend>Motivo</legend>
          @for (r of reasons; track r) {
            <label class="option">
              <input type="radio" name="reason" [value]="r" [checked]="reason() === r" (change)="reason.set(r)" />
              <span>
                <strong>{{ labels[r] }}</strong>
                <small>{{ hints[r] }}</small>
              </span>
            </label>
          }
        </fieldset>
        <label class="field">
          <span>Observação (opcional)</span>
          <textarea class="textarea" maxlength="2000" [value]="note()" (input)="note.set($any($event.target).value)"
            placeholder="Ex.: impressora do RH, compartilhamento necessário"></textarea>
        </label>
        <label class="check">
          <input type="checkbox" [checked]="suppress()" (change)="suppress.set($any($event.target).checked)" />
          <span>Não alertar novamente sobre o mesmo problema (novas ocorrências só serão contadas)</span>
        </label>
        <div class="actions">
          <button class="btn btn-ghost" type="button" (click)="cancel.emit()">Cancelar</button>
          <button class="btn btn-primary" type="button" (click)="confirm.emit({ reason: reason(), note: note(), suppress: suppress() })" [disabled]="busy()">
            @if (busy()) { <span class="spinner"></span> }
            Descartar
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.55); display: grid; place-items: center; z-index: 50; padding: 16px; }
    .dialog { width: min(480px, 100%); display: flex; flex-direction: column; gap: 14px; }
    h2 { margin: 0; font-size: 16px; }
    fieldset { border: 0; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
    legend { font-size: 12.5px; color: var(--text-secondary); margin-bottom: 6px; }
    .option { display: flex; gap: 10px; align-items: flex-start; padding: 8px 10px; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); cursor: pointer; }
    .option input { accent-color: var(--accent); margin-top: 3px; }
    .option span { display: flex; flex-direction: column; gap: 2px; }
    .option small { color: var(--text-muted); font-size: 12px; }
    .check { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; color: var(--text-secondary); cursor: pointer; }
    .check input { accent-color: var(--accent); margin-top: 3px; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; }
  `,
})
export class DismissDialog {
  readonly count = input(1);
  readonly busy = input(false);
  readonly confirm = output<DismissChoice>();
  readonly cancel = output<void>();

  protected readonly reasons: DismissReason[] = ['FALSE_POSITIVE', 'ACCEPTED_RISK', 'OTHER'];
  protected readonly labels = DISMISS_REASON_LABEL;
  protected readonly hints: Record<DismissReason, string> = {
    FALSE_POSITIVE: 'A regra se enganou: não há problema real.',
    ACCEPTED_RISK: 'O problema existe, mas é conhecido e aceito.',
    OTHER: 'Explique na observação.',
  };

  protected readonly reason = signal<DismissReason>('FALSE_POSITIVE');
  protected readonly note = signal('');
  protected readonly suppress = signal(false);
}
