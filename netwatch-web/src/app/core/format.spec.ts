import { HttpErrorResponse } from '@angular/common/http';

import { describeError } from './error.interceptor';
import { byteScale, deviceTypeLabel, endpoint, formatBytes, formatDuration, timeAgo } from './format';

describe('format', () => {
  it('formata durações', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(450)).toBe('450 ms');
    expect(formatDuration(10416)).toBe('10,4 s');
    expect(formatDuration(125000)).toBe('2 min 5 s');
  });

  it('formata tempo relativo', () => {
    const now = new Date('2026-11-09T12:00:00Z').getTime();
    expect(timeAgo(null)).toBe('—');
    expect(timeAgo('2026-11-09T11:59:50Z', now)).toBe('agora');
    expect(timeAgo('2026-11-09T11:55:00Z', now)).toBe('há 5 minutos');
    expect(timeAgo('2026-11-09T09:00:00Z', now)).toBe('há 3 horas');
  });

  it('traduz tipos de dispositivo', () => {
    expect(deviceTypeLabel('PRINTER')).toBe('Impressora');
    expect(deviceTypeLabel('UNKNOWN')).toBe('Não definido');
  });
});

describe('describeError', () => {
  it('usa o detalhe do Problem Details', () => {
    const error = new HttpErrorResponse({ status: 400, error: { title: 'Dados inválidos', detail: 'Endereço IP inválido: x' } });
    expect(describeError(error)).toBe('Endereço IP inválido: x');
  });

  it('prioriza erros de campo', () => {
    const error = new HttpErrorResponse({ status: 400, error: { detail: 'geral', errors: { ipAddress: 'O endereço IP é obrigatório.' } } });
    expect(describeError(error)).toBe('O endereço IP é obrigatório.');
  });

  it('explica quando a API está fora do ar', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('Não foi possível conectar à API');
  });

  it('formata volumes de dados', () => {
    expect(formatBytes(null)).toBe('—');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1,5 KB');
    expect(formatBytes(250 * 1024 * 1024)).toBe('250 MB');
    expect(byteScale(5 * 1024 * 1024)).toEqual({ unit: 'MB', divisor: 1024 * 1024 });
    expect(byteScale(10)).toEqual({ unit: 'B', divisor: 1 });
  });

  it('mostra IP e porta juntos', () => {
    expect(endpoint('192.168.0.1', 443)).toBe('192.168.0.1:443');
    expect(endpoint('192.168.0.1', null)).toBe('192.168.0.1');
  });
});
