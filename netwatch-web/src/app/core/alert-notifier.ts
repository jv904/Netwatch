import { Injectable, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';

import { Api } from './api';
import { ALERT_SEVERITY_LABEL } from './format';
import { AlertCounts } from './models';
import { ToastService } from './toast';

const STORAGE_KEY = 'netwatch.alertNotifications';

/**
 * Acompanha os contadores de alertas (contador do menu) e avisa quando surge alerta novo de gravidade
 * alta ou crítica: aviso na tela e, se o usuário ativou, notificação do navegador.
 */
@Injectable({ providedIn: 'root' })
export class AlertNotifier {
  private readonly api = inject(Api);
  private readonly toast = inject(ToastService);

  readonly counts = signal<AlertCounts | null>(null);
  readonly browserNotifications = signal(readPreference());

  /** Último alerta já conhecido; nulo antes da primeira consulta (não avisa o que já existia). */
  private lastSeen: string | null | undefined = undefined;

  /** Consulta os contadores e avisa sobre alertas novos. Chamado periodicamente pelo menu. */
  refresh(): void {
    this.api
      .alertCounts()
      .pipe(catchError(() => of(null)))
      .subscribe((counts) => {
        if (!counts) return;
        this.counts.set(counts);
        const previous = this.lastSeen;
        this.lastSeen = counts.lastRaisedAt;
        if (previous !== undefined && counts.lastRaisedAt && counts.lastRaisedAt !== previous) {
          this.announceNewAlerts(previous);
        }
      });
  }

  /** Ativa ou desativa as notificações do navegador (pede permissão na primeira vez). */
  async setBrowserNotifications(enabled: boolean): Promise<void> {
    if (!enabled) {
      this.browserNotifications.set(false);
      savePreference(false);
      return;
    }
    if (typeof Notification === 'undefined') {
      this.toast.error('Este navegador não suporta notificações.');
      return;
    }
    const permission = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
    if (permission !== 'granted') {
      this.toast.error('As notificações foram bloqueadas no navegador.');
      return;
    }
    this.browserNotifications.set(true);
    savePreference(true);
    this.toast.success('Notificações do navegador ativadas para alertas altos e críticos.');
  }

  private announceNewAlerts(since: string | null): void {
    this.api
      .securityAlerts({ state: 'active', size: 10 })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
        const fresh = (page?.content ?? [])
          .filter((a) => !since || a.firstSeenAt > since)
          .filter((a) => a.severity === 'HIGH' || a.severity === 'CRITICAL')
          .slice(0, 3);
        for (const alert of fresh) {
          const label = ALERT_SEVERITY_LABEL[alert.severity];
          this.toast.error(`Alerta ${label.toLowerCase()}: ${alert.title}`);
          if (this.browserNotifications() && typeof Notification !== 'undefined' && Notification.permission === 'granted') {
            const n = new Notification(`NetWatch — alerta ${label.toLowerCase()}`, {
              body: alert.title,
              tag: alert.id,
            });
            n.onclick = () => {
              window.focus();
              window.location.assign(`/alerts?id=${alert.id}`);
            };
          }
        }
      });
  }
}

function readPreference(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

function savePreference(enabled: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, enabled ? '1' : '0');
  } catch {
    // Sem armazenamento local (modo privado): a preferência vale só nesta sessão.
  }
}
