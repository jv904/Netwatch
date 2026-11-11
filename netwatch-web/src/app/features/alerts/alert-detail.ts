import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { Api } from '../../core/api';
import { ALERT_STATUS_LABEL, DISMISS_REASON_LABEL, formatDateTime, timeAgo } from '../../core/format';
import { AlertDetail } from '../../core/models';
import { ToastService } from '../../core/toast';
import { AlertSeverityChip } from '../../shared/chips';
import { Icon } from '../../shared/icon';
import { DismissChoice, DismissDialog } from './dismiss-dialog';

/** Painel de um alerta: o que foi detectado, o que fazer, ações e linha do tempo. */
@Component({
  selector: 'app-alert-detail',
  imports: [AlertSeverityChip, DismissDialog, Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alert-detail.html',
  styleUrl: './alert-detail.scss',
})
export class AlertDetailPanel {
  private readonly api = inject(Api);
  private readonly toast = inject(ToastService);

  readonly alertId = input.required<string>();
  /** O alerta mudou (situação ou comentário): a lista deve recarregar. */
  readonly changed = output<void>();
  readonly closed = output<void>();

  protected readonly detail = signal<AlertDetail | null>(null);
  protected readonly notFound = signal(false);
  protected readonly busy = signal(false);
  protected readonly note = signal('');
  protected readonly comment = signal('');
  protected readonly dismissing = signal(false);

  protected readonly statusLabel = ALERT_STATUS_LABEL;
  protected readonly reasonLabel = DISMISS_REASON_LABEL;
  protected readonly formatDateTime = formatDateTime;
  protected readonly timeAgo = timeAgo;

  protected readonly isActive = computed(() => {
    const s = this.detail()?.alert.status;
    return s === 'OPEN' || s === 'ACKNOWLEDGED';
  });

  constructor() {
    effect(() => this.load(this.alertId()));
  }

  private load(id: string): void {
    this.notFound.set(false);
    this.detail.set(null);
    this.note.set('');
    this.comment.set('');
    this.api.alertDetail(id).subscribe({
      next: (d) => this.detail.set(d),
      error: () => this.notFound.set(true),
    });
  }

  protected acknowledge(): void {
    this.run(this.api.acknowledgeAlert(this.alertId(), this.note()), 'Alerta reconhecido.');
  }

  protected resolve(): void {
    this.run(this.api.resolveAlert(this.alertId(), this.note()), 'Alerta resolvido.');
  }

  protected reopen(): void {
    this.run(this.api.reopenAlert(this.alertId(), this.note()), 'Alerta reaberto.');
  }

  protected dismiss(choice: DismissChoice): void {
    this.run(this.api.dismissAlert(this.alertId(), choice.reason, choice.note, choice.suppress), 'Alerta descartado.', () =>
      this.dismissing.set(false),
    );
  }

  protected addComment(): void {
    const text = this.comment().trim();
    if (!text) return;
    this.run(this.api.commentAlert(this.alertId(), text), 'Comentário registrado.', () => this.comment.set(''));
  }

  private run(request: Observable<AlertDetail>, message: string, after?: () => void): void {
    this.busy.set(true);
    request.subscribe({
      next: (d) => {
        this.busy.set(false);
        this.detail.set(d);
        this.note.set('');
        after?.();
        this.toast.success(message);
        this.changed.emit();
      },
      error: () => this.busy.set(false),
    });
  }
}
