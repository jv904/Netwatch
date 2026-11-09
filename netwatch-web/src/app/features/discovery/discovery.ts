import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, catchError, of, switchMap, takeWhile, timer } from 'rxjs';

import { Api } from '../../core/api';
import { DiscoveryEvents } from '../../core/discovery-events';
import { formatDateTime, formatDuration, timeAgo } from '../../core/format';
import { AuthorizedNetwork, DiscoveryRun, Page } from '../../core/models';
import { ToastService } from '../../core/toast';
import { RunStatusChip } from '../../shared/chips';
import { Icon } from '../../shared/icon';
import { Paginator } from '../../shared/paginator';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-discovery',
  imports: [RunStatusChip, Icon, Paginator, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './discovery.html',
  styleUrl: './discovery.scss',
})
export class Discovery {
  private readonly api = inject(Api);
  private readonly toast = inject(ToastService);

  protected readonly networks = signal<AuthorizedNetwork[] | null>(null);
  protected readonly selected = signal<string>('');
  protected readonly customCidr = signal('');
  protected readonly current = signal<DiscoveryRun | null>(null);
  protected readonly history = signal<Page<DiscoveryRun> | null>(null);
  protected readonly page = signal(0);
  protected readonly starting = signal(false);
  protected readonly now = signal(Date.now());

  protected readonly formatDateTime = formatDateTime;
  protected readonly formatDuration = formatDuration;
  protected readonly timeAgo = timeAgo;

  protected readonly elapsed = computed(() => {
    const run = this.current();
    return run ? formatDuration(this.now() - new Date(run.startedAt).getTime()) : '';
  });

  /** Faixa que será enviada: a digitada tem prioridade sobre a rede selecionada. */
  protected readonly target = computed(() => this.customCidr().trim() || this.selected());

  private polling?: Subscription;

  constructor() {
    this.api.networks().pipe(catchError(() => of([]))).subscribe((list) => {
      this.networks.set(list);
      if (list.length) this.selected.set(list[0].cidr);
    });
    this.loadHistory();

    // Roda já na criação e novamente a cada descoberta iniciada pela barra superior.
    const events = inject(DiscoveryEvents);
    effect(() => {
      events.started();
      untracked(() => this.watchCurrent());
    });

    const clock = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(clock);
      this.polling?.unsubscribe();
    });
  }

  protected start(): void {
    this.starting.set(true);
    this.api.startDiscovery(this.target() || undefined).subscribe({
      next: (run) => {
        this.starting.set(false);
        this.current.set(run);
        this.watchCurrent();
      },
      error: () => this.starting.set(false),
    });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.loadHistory();
  }

  /** Acompanha a execução em andamento (a API devolve 204/null quando não há nenhuma). */
  private watchCurrent(): void {
    this.polling?.unsubscribe();
    let seenRunning = false;
    this.polling = timer(0, 2000)
      .pipe(
        switchMap(() => this.api.currentDiscovery().pipe(catchError(() => of(null)))),
        takeWhile((run) => run !== null, true),
      )
      .subscribe((run) => {
        this.current.set(run);
        if (run) {
          seenRunning = true;
          return;
        }
        if (seenRunning) {
          this.loadHistory(true);
        }
      });
  }

  private loadHistory(announce = false): void {
    this.api
      .discoveryRuns(this.page(), PAGE_SIZE)
      .pipe(catchError(() => of(null)))
      .subscribe((page) => {
        this.history.set(page);
        const last = page?.content[0];
        if (announce && last) {
          if (last.status === 'COMPLETED') {
            this.toast.success(`Descoberta concluída: ${last.devicesFound} dispositivos, ${last.newDevices} novos.`);
          } else if (last.status === 'FAILED') {
            this.toast.error(`A descoberta falhou: ${last.errorMessage ?? 'erro desconhecido'}`);
          }
        }
      });
  }
}
