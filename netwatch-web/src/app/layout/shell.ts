import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, of, switchMap, timer } from 'rxjs';

import { Api } from '../core/api';
import { DiscoveryEvents } from '../core/discovery-events';
import { ToastService } from '../core/toast';
import { Icon, IconName } from '../shared/icon';

interface NavItem {
  label: string;
  description: string;
  icon: IconName;
  link?: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly discoveryEvents = inject(DiscoveryEvents);
  protected readonly toast = inject(ToastService);

  /** Seções do manual (seção 2). As ainda não construídas aparecem como "Em breve". */
  protected readonly nav: NavItem[] = [
    { label: 'Dashboard', description: 'Visão geral da rede', icon: 'dashboard', link: '/dashboard' },
    { label: 'Dispositivos', description: 'Equipamentos identificados', icon: 'devices', link: '/devices' },
    { label: 'Descoberta', description: 'Varredura da rede', icon: 'radar', link: '/discovery' },
    { label: 'Portas e serviços', description: 'Exposição dos dispositivos', icon: 'ports', link: '/ports' },
    { label: 'Tráfego', description: 'Captura de pacotes', icon: 'traffic', link: '/traffic' },
    { label: 'Atividades', description: 'Histórico de eventos', icon: 'activity', link: '/activity' },
    { label: 'Segurança', description: 'Regras e detecções', icon: 'shield', link: '/security' },
    { label: 'Alertas', description: 'Gerenciar alertas', icon: 'bell' },
    { label: 'Relatórios', description: 'Histórico e análises', icon: 'report' },
    { label: 'Configurações', description: 'Preferências do sistema', icon: 'settings' },
  ];

  protected readonly apiOnline = signal<boolean | null>(null);
  protected readonly menuOpen = signal(false);
  protected readonly startingDiscovery = signal(false);
  protected readonly search = signal('');

  constructor() {
    timer(0, 30_000)
      .pipe(
        switchMap(() => this.api.health().pipe(catchError(() => of(false)))),
        takeUntilDestroyed(),
      )
      .subscribe((online) => this.apiOnline.set(online));

    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  protected submitSearch(event: Event): void {
    event.preventDefault();
    const q = this.search().trim();
    this.router.navigate(['/devices'], { queryParams: q ? { q } : {} });
  }

  protected startDiscovery(): void {
    this.startingDiscovery.set(true);
    this.api.startDiscovery().subscribe({
      next: (run) => {
        this.startingDiscovery.set(false);
        this.discoveryEvents.notifyStarted();
        this.toast.info(`Descoberta iniciada em ${run.cidr}.`);
        this.router.navigate(['/discovery']);
      },
      error: () => this.startingDiscovery.set(false),
    });
  }
}
