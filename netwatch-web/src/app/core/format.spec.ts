import { HttpErrorResponse } from '@angular/common/http';

import { describeError } from './error.interceptor';
import { deviceTypeLabel, formatDuration, timeAgo } from './format';

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
});
