import { DeviceType, PortState, RunStatus } from './models';

export const DEVICE_TYPES: { value: DeviceType; label: string }[] = [
  { value: 'UNKNOWN', label: 'Não definido' },
  { value: 'COMPUTER', label: 'Computador' },
  { value: 'NOTEBOOK', label: 'Notebook' },
  { value: 'SMARTPHONE', label: 'Celular' },
  { value: 'TABLET', label: 'Tablet' },
  { value: 'PRINTER', label: 'Impressora' },
  { value: 'SERVER', label: 'Servidor' },
  { value: 'ROUTER', label: 'Roteador' },
  { value: 'SWITCH', label: 'Switch' },
  { value: 'ACCESS_POINT', label: 'Ponto de acesso' },
  { value: 'IOT', label: 'IoT / câmera' },
  { value: 'OTHER', label: 'Outro' },
];

export function deviceTypeLabel(type: DeviceType): string {
  return DEVICE_TYPES.find((t) => t.value === type)?.label ?? type;
}

export const PORT_STATE_LABEL: Record<PortState, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  FILTERED: 'Sem resposta',
};

export const RUN_STATUS_LABEL: Record<RunStatus, string> = {
  RUNNING: 'Em andamento',
  COMPLETED: 'Concluída',
  FAILED: 'Falhou',
};

const relative = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' });

/** "há 5 minutos", "ontem"... */
export function timeAgo(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return '—';
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) return 'agora';
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return relative.format(Math.round(seconds / 3600), 'hour');
  if (abs < 86400 * 30) return relative.format(Math.round(seconds / 86400), 'day');
  return new Date(iso).toLocaleDateString('pt-BR');
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—';
  if (ms < 1000) return `${ms} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s`;
  const m = Math.floor(s / 60);
  return `${m} min ${Math.round(s % 60)} s`;
}

export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toLocaleString('pt-BR');
}
