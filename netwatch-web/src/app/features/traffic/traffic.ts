import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subscription, catchError, combineLatest, debounceTime, forkJoin, of, switchMap, timer } from 'rxjs';

import { Api } from '../../core/api';
import { byteScale, endpoint, formatBytes, formatDateTime, formatDuration, timeAgo } from '../../core/format';
import {
  CaptureInterface,
  CaptureSession,
  CaptureState,
  Page,
  TrafficFlow,
  TrafficProtocol,
  TrafficSummary,
  TrafficWindow,
} from '../../core/models';
import { ToastService } from '../../core/toast';
import { AreaChart, AreaPoint } from '../../shared/area-chart';
import { BarChart, BarItem } from '../../shared/bar-chart';
import { CaptureStatusChip } from '../../shared/chips';
import { Icon } from '../../shared/icon';
import { KpiCard } from '../../shared/kpi-card';
import { Paginator } from '../../shared/paginator';

const FLOW_PAGE_SIZE = 25;
const SESSION_PAGE_SIZE = 5;
/** Atualização enquanto há captura em andamento (o backend grava a cada ~10 s). */
const LIVE_REFRESH_MS = 5000;

@Component({
  selector: 'app-traffic',
  imports: [AreaChart, BarChart, CaptureStatusChip, Icon, KpiCard, Paginator, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './traffic.html',
  styleUrl: './traffic.scss',
})
export class Traffic {
  private readonly api = inject(Api);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<CaptureState | null>(null);
  protected readonly stateError = signal(false);
  protected readonly interfaces = signal<CaptureInterface[]>([]);
  protected readonly selectedInterface = signal('');
  protected readonly duration = signal(60);
  protected readonly starting = signal(false);
  protected readonly stopping = signal(false);

  protected readonly window = signal<TrafficWindow>('1h');
  protected readonly summary = signal<TrafficSummary | null>(null);

  protected readonly ipFilter = signal('');
  protected readonly protocolFilter = signal<TrafficProtocol | ''>('');
  protected readonly portFilter = signal<number | null>(null);
  protected readonly flowPage = signal(0);
  protected readonly flows = signal<Page<TrafficFlow> | null>(null);

  protected readonly sessionPage = signal(0);
  protected readonly sessions = signal<Page<CaptureSession> | null>(null);

  protected readonly now = signal(Date.now());

  protected readonly formatBytes = formatBytes;
  protected readonly formatDateTime = formatDateTime;
  protected readonly formatDuration = formatDuration;
  protected readonly timeAgo = timeAgo;
  protected readonly endpoint = endpoint;

  protected readonly windows: { value: TrafficWindow; label: string }[] = [
    { value: '1h', label: 'Última hora' },
    { value: '24h', label: '24 horas' },
    { value: '7d', label: '7 dias' },
  ];
  protected readonly protocols: TrafficProtocol[] = ['TCP', 'UDP', 'ICMP', 'IGMP', 'ARP', 'OTHER'];

  protected readonly active = computed(() => this.state()?.activeSession ?? null);
  protected readonly eligibleInterfaces = computed(() => this.interfaces().filter((i) => i.eligible));
  protected readonly durationOptions = computed(() => {
    const max = this.state()?.maxDurationMinutes ?? 480;
    return [5, 15, 30, 60, 120, 240, 480].filter((m) => m <= max);
  });

  protected readonly elapsed = computed(() => {
    const s = this.active();
    return s ? formatDuration(this.now() - new Date(s.startedAt).getTime()) : '';
  });
  protected readonly remaining = computed(() => {
    const s = this.active();
    return s ? formatDuration(Math.max(0, new Date(s.plannedEndAt).getTime() - this.now())) : '';
  });

  /** Sessão atual (ou próxima) usa a tabela de conexões: não há volume nem pacotes. */
  protected readonly connectionsMode = computed(
    () => (this.active()?.mode ?? this.state()?.mode) === 'CONNECTIONS',
  );

  /** Há volume medido no período? Sem ele (tabela de conexões), os gráficos usam conexões e fluxos. */
  protected readonly hasVolume = computed(() => (this.summary()?.totals.bytes ?? 0) > 0);

  /** Vazão em uma unidade comum (B/KB/MB...) ou, sem volume, o pico de conexões simultâneas. */
  protected readonly volumeChart = computed(() => {
    const s = this.summary();
    if (!s || (s.totals.bytes === 0 && s.totals.connections === 0)) {
      return { points: [] as AreaPoint[], unit: '', title: this.connectionsMode() ? 'Conexões ativas' : 'Volume de tráfego' };
    }
    const short = s.bucketSeconds >= 3600 || s.window !== '1h';
    const label = (at: string) => {
      const d = new Date(at);
      return short
        ? d.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
        : d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    };
    if (s.totals.bytes === 0) {
      const points = s.series.map((p) => ({ label: label(p.at), value: p.connections }));
      return { points, unit: 'conexões', title: 'Conexões ativas' };
    }
    const scale = byteScale(Math.max(...s.series.map((p) => p.bytes)));
    const points = s.series.map((p) => ({
      label: label(p.at),
      value: Math.round((p.bytes / scale.divisor) * 10) / 10,
      detail: `${p.packets.toLocaleString('pt-BR')} pacotes`,
    }));
    return { points, unit: scale.unit, title: 'Volume de tráfego' };
  });

  protected readonly hostBars = computed(() => {
    const hosts = this.summary()?.topHosts ?? [];
    const byVolume = this.hasVolume();
    const scale = byteScale(Math.max(0, ...hosts.map((h) => h.bytesSent + h.bytesReceived)));
    const items: BarItem[] = hosts.map((h) => ({
      label: h.name ? `${h.name} · ${h.ip}` : h.ip,
      value: byVolume ? Math.round(((h.bytesSent + h.bytesReceived) / scale.divisor) * 10) / 10 : h.flows,
      hint: byVolume
        ? `enviou ${formatBytes(h.bytesSent)}, recebeu ${formatBytes(h.bytesReceived)}${h.local ? '' : ' (fora da rede local)'}`
        : `${h.local ? 'rede local' : 'fora da rede local'} — clique para ver os fluxos`,
    }));
    return { items, unit: byVolume ? scale.unit : 'fluxos', ips: hosts.map((h) => h.ip) };
  });

  protected readonly serviceBars = computed(() => {
    const services = this.summary()?.topServices ?? [];
    const byVolume = this.hasVolume();
    const scale = byteScale(Math.max(0, ...services.map((s) => s.bytes)));
    const items: BarItem[] = services.map((s) => ({
      label: `${s.port}/${s.protocol.toLowerCase()} · ${s.service ?? 'Não identificado'}`,
      value: byVolume ? Math.round((s.bytes / scale.divisor) * 10) / 10 : s.flows,
      hint: byVolume
        ? `${s.flows.toLocaleString('pt-BR')} fluxos, ${s.packets.toLocaleString('pt-BR')} pacotes`
        : 'clique para ver os fluxos',
      port: s.port,
    }));
    return { items, unit: byVolume ? scale.unit : 'fluxos' };
  });

  protected readonly protocolShares = computed(() => {
    const s = this.summary();
    if (!s || s.totals.bytes === 0) return [];
    return s.protocols.map((p) => ({ ...p, share: Math.round((p.bytes / s.totals.bytes) * 1000) / 10 }));
  });

  private live?: Subscription;

  constructor() {
    this.loadState(true);
    this.loadSessions();

    toObservable(this.window)
      .pipe(
        switchMap((w) => this.api.trafficSummary(w).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((s) => this.summary.set(s));

    combineLatest([
      toObservable(this.window),
      toObservable(this.ipFilter).pipe(debounceTime(300)),
      toObservable(this.protocolFilter),
      toObservable(this.portFilter).pipe(debounceTime(300)),
      toObservable(this.flowPage),
    ])
      .pipe(
        switchMap(([window, ip, protocol, port, page]) =>
          this.api
            .trafficFlows({ window, ip, protocol, port, page, size: FLOW_PAGE_SIZE })
            .pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((p) => this.flows.set(p));

    const clock = setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => {
      clearInterval(clock);
      this.live?.unsubscribe();
    });
  }

  protected start(): void {
    this.starting.set(true);
    const iface = this.connectionsMode() ? undefined : this.selectedInterface() || undefined;
    this.api.startCapture(iface, this.duration()).subscribe({
      next: (session) => {
        this.starting.set(false);
        this.toast.info(
          session.mode === 'CONNECTIONS'
            ? 'Observação iniciada pela tabela de conexões do sistema.'
            : `Captura iniciada em ${session.interfaceDescription ?? session.interfaceName}.`,
        );
        this.loadState();
        this.loadSessions();
      },
      error: () => this.starting.set(false),
    });
  }

  protected stop(): void {
    const session = this.active();
    if (!session) return;
    this.stopping.set(true);
    this.api.stopCapture(session.id).subscribe({
      next: () => {
        this.stopping.set(false);
        // A sessão termina em instantes; o acompanhamento ao vivo percebe e atualiza a tela.
      },
      error: () => this.stopping.set(false),
    });
  }

  protected setWindow(window: TrafficWindow): void {
    this.window.set(window);
    this.flowPage.set(0);
  }

  protected setIp(value: string): void {
    this.ipFilter.set(value);
    this.flowPage.set(0);
  }

  protected setProtocol(value: string): void {
    this.protocolFilter.set(value as TrafficProtocol | '');
    this.flowPage.set(0);
  }

  protected setPort(value: string): void {
    const port = Number.parseInt(value, 10);
    this.portFilter.set(Number.isFinite(port) && port > 0 ? port : null);
    this.flowPage.set(0);
  }

  protected filterByHost(item: BarItem): void {
    const index = this.hostBars().items.indexOf(item);
    const ip = this.hostBars().ips[index];
    if (ip) {
      this.setIp(ip);
      document.getElementById('fluxos')?.scrollIntoView({ behavior: 'smooth' });
    }
  }

  protected filterByService(item: BarItem): void {
    if (item.port) {
      this.portFilter.set(item.port);
      this.flowPage.set(0);
      document.getElementById('fluxos')?.scrollIntoView({ behavior: 'smooth' });
    }
  }

  protected clearFilters(): void {
    this.ipFilter.set('');
    this.protocolFilter.set('');
    this.portFilter.set(null);
    this.flowPage.set(0);
  }

  protected goToSessionPage(page: number): void {
    this.sessionPage.set(page);
    this.loadSessions();
  }

  private loadState(withInterfaces = false): void {
    this.api
      .captureStatus()
      .pipe(catchError(() => of(null)))
      .subscribe((state) => {
        this.stateError.set(state === null);
        this.state.set(state);
        if (state) {
          if (this.duration() > state.maxDurationMinutes || withInterfaces) {
            this.duration.set(Math.min(state.defaultDurationMinutes, state.maxDurationMinutes));
          }
          if (withInterfaces && state.mode === 'PACKETS') this.loadInterfaces();
          if (state.activeSession) this.goLive();
        }
      });
  }

  private loadInterfaces(): void {
    this.api
      .captureInterfaces()
      .pipe(catchError(() => of([] as CaptureInterface[])))
      .subscribe((list) => {
        this.interfaces.set(list);
        const first = list.find((i) => i.eligible);
        if (first && !this.selectedInterface()) this.selectedInterface.set(first.name);
      });
  }

  private loadSessions(): void {
    this.api
      .captureSessions(this.sessionPage(), SESSION_PAGE_SIZE)
      .pipe(catchError(() => of(null)))
      .subscribe((page) => this.sessions.set(page));
  }

  private refreshData(): void {
    forkJoin({
      summary: this.api.trafficSummary(this.window()).pipe(catchError(() => of(null))),
      flows: this.api
        .trafficFlows({
          window: this.window(),
          ip: this.ipFilter(),
          protocol: this.protocolFilter(),
          port: this.portFilter(),
          page: this.flowPage(),
          size: FLOW_PAGE_SIZE,
        })
        .pipe(catchError(() => of(null))),
    }).subscribe(({ summary, flows }) => {
      if (summary) this.summary.set(summary);
      if (flows) this.flows.set(flows);
    });
  }

  /** Enquanto houver captura, atualiza situação, gráficos e fluxos periodicamente. */
  private goLive(): void {
    if (this.live && !this.live.closed) return;
    this.live = timer(LIVE_REFRESH_MS, LIVE_REFRESH_MS)
      .pipe(switchMap(() => this.api.captureStatus().pipe(catchError(() => of(null)))))
      .subscribe((state) => {
        if (!state) return;
        const wasActive = this.state()?.activeSession;
        this.state.set(state);
        this.refreshData();
        if (!state.activeSession) {
          this.live?.unsubscribe();
          this.loadSessions();
          const last = state.lastSession;
          if (wasActive && last) {
            if (last.status === 'FAILED') {
              this.toast.error(`A captura falhou: ${last.errorMessage ?? 'erro desconhecido'}`);
            } else if (last.mode === 'CONNECTIONS') {
              this.toast.success('Observação encerrada.');
            } else {
              this.toast.success(
                `Captura encerrada: ${last.packets.toLocaleString('pt-BR')} pacotes, ${formatBytes(last.bytes)}.`,
              );
            }
          }
        }
      });
  }
}
