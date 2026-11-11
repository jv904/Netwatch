import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ALERT_STATUS_LABEL, DISMISS_REASON_LABEL, formatDateTime, timeAgo } from '../core/format';
import { SecurityAlert } from '../core/models';
import { AlertSeverityChip } from './chips';

/**
 * Lista de alertas: gravidade, o que foi detectado, evidência, recomendação e dispositivo envolvido.
 * Opcionalmente com caixas de seleção (ações em lote) e clique para abrir o detalhe.
 */
@Component({
  selector: 'app-alert-list',
  imports: [AlertSeverityChip, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (alerts().length === 0) {
      <div class="empty">{{ emptyText() }}</div>
    } @else {
      <ul class="alerts" [attr.aria-label]="ariaLabel()">
        @for (a of alerts(); track a.id) {
          <li class="alert" [class]="a.severity.toLowerCase()" [class.active]="a.id === activeId()" [class.closed]="a.status === 'RESOLVED' || a.status === 'DISMISSED'">
            <div class="head">
              @if (selectable()) {
                <input
                  type="checkbox"
                  class="check"
                  [checked]="selected().includes(a.id)"
                  (change)="toggle.emit(a.id)"
                  [attr.aria-label]="'Selecionar alerta ' + a.title"
                />
              }
              <app-alert-severity [severity]="a.severity" />
              @if (clickable()) {
                <button type="button" class="title as-link" (click)="open.emit(a)">{{ a.title }}</button>
              } @else {
                <span class="title">{{ a.title }}</span>
              }
            </div>
            @if (a.evidence) {
              <div class="evidence">{{ a.evidence }}</div>
            }
            <div class="meta">
              <span>{{ a.ruleTitle }}</span>
              <span>·</span>
              <span [title]="'Primeira vez: ' + formatDateTime(a.firstSeenAt)">{{ timeAgo(a.lastSeenAt) }}</span>
              @if (a.occurrences > 1) {
                <span>·</span>
                <span>{{ a.occurrences }} ocorrências</span>
              }
              @if (showStatus()) {
                <span>·</span>
                <span class="status" [class]="a.status.toLowerCase()">
                  {{ statusLabel[a.status] }}{{ a.dismissReason ? ' (' + reasonLabel[a.dismissReason] + ')' : '' }}
                </span>
              }
              @if (a.deviceId && a.deviceName) {
                <span>·</span>
                <a class="link" [routerLink]="['/devices', a.deviceId]">{{ a.deviceName }}</a>
              }
            </div>
            @if (showRecommendation()) {
              <details>
                <summary>O que fazer</summary>
                <p>{{ a.recommendation }}</p>
              </details>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .alerts { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
    .alert { border: 1px solid var(--border); border-left: 3px solid var(--border-strong); border-radius: var(--radius-sm); padding: 10px 12px; background: var(--surface-2); }
    .alert.medium { border-left-color: var(--warning); }
    .alert.high { border-left-color: var(--serious); }
    .alert.critical { border-left-color: var(--critical); }
    .alert.active { outline: 1px solid var(--accent); background: var(--surface-3); }
    .alert.closed .title { color: var(--text-secondary); }
    .head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .check { accent-color: var(--accent); width: 15px; height: 15px; margin: 0; cursor: pointer; }
    .title { color: var(--text-primary); overflow-wrap: anywhere; }
    .as-link { background: none; border: 0; padding: 0; font: inherit; text-align: left; cursor: pointer; }
    .as-link:hover, .as-link:focus-visible { color: var(--accent); text-decoration: underline; }
    .evidence { color: var(--text-secondary); font-size: 12.5px; margin-top: 4px; overflow-wrap: anywhere; }
    .meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; font-size: 12px; color: var(--text-muted); }
    .status.open { color: var(--warning); }
    .status.acknowledged { color: var(--accent); }
    .status.resolved { color: var(--good); }
    details { margin-top: 6px; font-size: 12.5px; color: var(--text-secondary); }
    summary { cursor: pointer; color: var(--accent); width: fit-content; }
    details p { margin: 6px 0 0; }
    .link { color: var(--accent); }
    .empty { padding: 28px 0; text-align: center; color: var(--text-muted); }
  `,
})
export class AlertList {
  readonly alerts = input.required<SecurityAlert[]>();
  readonly showStatus = input(false);
  readonly showRecommendation = input(true);
  readonly emptyText = input('Nenhum alerta.');
  readonly ariaLabel = input('Alertas');
  /** O título vira botão que emite {@link open}. */
  readonly clickable = input(false);
  /** Mostra caixas de seleção; os marcados vêm em {@link selected}. */
  readonly selectable = input(false);
  readonly selected = input<string[]>([]);
  /** Alerta aberto no painel de detalhe (destacado na lista). */
  readonly activeId = input<string | null>(null);

  readonly open = output<SecurityAlert>();
  readonly toggle = output<string>();

  protected readonly statusLabel = ALERT_STATUS_LABEL;
  protected readonly reasonLabel = DISMISS_REASON_LABEL;
  protected readonly timeAgo = timeAgo;
  protected readonly formatDateTime = formatDateTime;
}
