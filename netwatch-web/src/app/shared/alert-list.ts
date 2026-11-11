import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ALERT_STATUS_LABEL, formatDateTime, timeAgo } from '../core/format';
import { SecurityAlert } from '../core/models';
import { AlertSeverityChip } from './chips';

/** Lista de alertas: gravidade, o que foi detectado, evidência, recomendação e dispositivo envolvido. */
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
          <li class="alert" [class]="a.severity.toLowerCase()">
            <div class="head">
              <app-alert-severity [severity]="a.severity" />
              <span class="title">{{ a.title }}</span>
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
                <span>{{ statusLabel[a.status] }}</span>
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
    .head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .title { color: var(--text-primary); overflow-wrap: anywhere; }
    .evidence { color: var(--text-secondary); font-size: 12.5px; margin-top: 4px; overflow-wrap: anywhere; }
    .meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; font-size: 12px; color: var(--text-muted); }
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

  protected readonly statusLabel = ALERT_STATUS_LABEL;
  protected readonly timeAgo = timeAgo;
  protected readonly formatDateTime = formatDateTime;
}
