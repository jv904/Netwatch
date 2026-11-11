import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { EVENT_SEVERITY_LABEL, EVENT_TYPE_LABEL, formatDateTime, timeAgo } from '../core/format';
import { EventCategory, NetworkEvent } from '../core/models';
import { Icon, IconName } from './icon';

const CATEGORY_ICON: Record<EventCategory, IconName> = {
  DEVICE: 'devices',
  PORT: 'ports',
  DISCOVERY: 'radar',
  TRAFFIC: 'traffic',
};

/**
 * Linha do tempo de eventos: ícone da categoria com a cor da importância, título, detalhes,
 * quando aconteceu e (opcionalmente) o dispositivo envolvido.
 */
@Component({
  selector: 'app-event-list',
  imports: [Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (events().length === 0) {
      <div class="empty">{{ emptyText() }}</div>
    } @else {
      <ol class="events" [attr.aria-label]="ariaLabel()">
        @for (e of events(); track e.id) {
          <li class="event" [class]="e.severity.toLowerCase()">
            <span class="marker" [title]="severityLabel[e.severity]">
              <app-icon [name]="categoryIcon[e.category]" [size]="14" />
            </span>
            <div class="body">
              <div class="title">{{ e.title }}</div>
              @if (e.details) {
                <div class="details">{{ e.details }}</div>
              }
              <div class="meta">
                <span [title]="formatDateTime(e.occurredAt)">{{ timeAgo(e.occurredAt) }}</span>
                <span>·</span>
                <span>{{ typeLabel[e.type] }}</span>
                @if (e.severity !== 'INFO') {
                  <span class="chip" [class.warning]="e.severity === 'WARNING'" [class.info]="e.severity === 'NOTICE'">
                    {{ severityLabel[e.severity] }}
                  </span>
                }
                @if (showDevice() && e.deviceId && e.deviceName) {
                  <span>·</span>
                  <a class="link" [routerLink]="['/devices', e.deviceId]">{{ e.deviceName }}</a>
                }
              </div>
            </div>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .events { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .event { display: grid; grid-template-columns: 28px 1fr; gap: 12px; padding: 10px 0; position: relative; }
    .event + .event { border-top: 1px solid var(--border); }
    .marker {
      width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center;
      background: var(--surface-2); color: var(--text-muted);
    }
    .event.warning .marker { background: var(--warning-soft); color: var(--warning); }
    .event.notice .marker { background: var(--accent-soft); color: var(--accent); }
    .body { min-width: 0; }
    .title { color: var(--text-primary); overflow-wrap: anywhere; }
    .details { color: var(--text-secondary); font-size: 12.5px; margin-top: 2px; overflow-wrap: anywhere; }
    .meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 4px; font-size: 12px; color: var(--text-muted); }
    .meta .chip { font-size: 11px; padding: 1px 6px; }
    .link { color: var(--accent); }
    .empty { padding: 28px 0; text-align: center; color: var(--text-muted); }
  `,
})
export class EventList {
  readonly events = input.required<NetworkEvent[]>();
  readonly showDevice = input(true);
  readonly emptyText = input('Nenhum evento registrado.');
  readonly ariaLabel = input('Histórico de eventos');

  protected readonly categoryIcon = CATEGORY_ICON;
  protected readonly severityLabel = EVENT_SEVERITY_LABEL;
  protected readonly typeLabel = EVENT_TYPE_LABEL;
  protected readonly timeAgo = timeAgo;
  protected readonly formatDateTime = formatDateTime;
}
