import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { AlertNotifier } from '../../core/alert-notifier';
import { Api } from '../../core/api';
import { formatDateTime, formatDuration } from '../../core/format';
import { SettingItem, SystemInfo } from '../../core/models';
import { ToastService } from '../../core/toast';
import { Icon } from '../../shared/icon';

/** Rótulos das opções de escolha. */
const CHOICE_LABELS: Record<string, string> = {
  auto: 'Automático',
  packets: 'Captura de pacotes (Npcap/libpcap)',
  connections: 'Tabela de conexões do sistema',
};

@Component({
  selector: 'app-settings',
  imports: [Icon, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  private readonly api = inject(Api);
  private readonly toast = inject(ToastService);
  protected readonly notifier = inject(AlertNotifier);

  protected readonly items = signal<SettingItem[] | null>(null);
  /** Valores editados ainda não salvos (chave → valor). */
  protected readonly draft = signal<Record<string, string>>({});
  protected readonly saving = signal(false);
  protected readonly system = signal<SystemInfo | null>(null);

  protected readonly formatDateTime = formatDateTime;
  protected readonly choiceLabels = CHOICE_LABELS;

  protected readonly groups = computed(() => {
    const groups = new Map<string, { title: string; items: SettingItem[] }>();
    for (const item of this.items() ?? []) {
      const g = groups.get(item.group) ?? { title: item.groupTitle, items: [] };
      g.items.push(item);
      groups.set(item.group, g);
    }
    return [...groups.values()];
  });

  protected readonly changes = computed(() => {
    const byKey = new Map((this.items() ?? []).map((i) => [i.key, i.value]));
    return Object.entries(this.draft()).filter(([key, value]) => byKey.get(key) !== value);
  });

  protected readonly uptime = computed(() => {
    const s = this.system();
    return s ? formatDuration(s.uptimeSeconds * 1000) : '';
  });

  constructor() {
    this.load();
  }

  protected current(item: SettingItem): string {
    return this.draft()[item.key] ?? item.value;
  }

  protected set(item: SettingItem, value: string): void {
    this.draft.update((d) => ({ ...d, [item.key]: value }));
  }

  protected toggle(item: SettingItem, event: Event): void {
    this.set(item, String((event.target as HTMLInputElement).checked));
  }

  protected display(item: SettingItem, value: string): string {
    if (item.type === 'BOOLEAN') return value === 'true' ? 'ligado' : 'desligado';
    if (item.type === 'CHOICE') return CHOICE_LABELS[value] ?? value;
    if (item.type === 'CIDR_LIST') return value || 'detectar pelas placas de rede';
    return item.unit ? `${value} ${item.unit}` : value;
  }

  protected save(): void {
    const changes = Object.fromEntries(this.changes());
    if (!Object.keys(changes).length) return;
    this.saving.set(true);
    this.api.updateSettings(changes).subscribe({
      next: (items) => {
        this.saving.set(false);
        this.items.set(items);
        this.draft.set({});
        this.toast.success('Configurações salvas. Já estão valendo.');
        this.loadSystem();
      },
      error: () => this.saving.set(false),
    });
  }

  protected discard(): void {
    this.draft.set({});
  }

  protected restore(item: SettingItem): void {
    this.api.restoreSetting(item.key).subscribe((items) => {
      this.items.set(items);
      this.draft.update((d) => {
        const next = { ...d };
        delete next[item.key];
        return next;
      });
      this.toast.success(`"${item.label}" voltou ao padrão.`);
      this.loadSystem();
    });
  }

  protected toggleNotifications(event: Event): void {
    void this.notifier.setBrowserNotifications((event.target as HTMLInputElement).checked);
  }

  private load(): void {
    this.api
      .settings()
      .pipe(catchError(() => of(null)))
      .subscribe((items) => this.items.set(items));
    this.loadSystem();
  }

  private loadSystem(): void {
    this.api
      .systemInfo()
      .pipe(catchError(() => of(null)))
      .subscribe((s) => this.system.set(s));
  }
}
