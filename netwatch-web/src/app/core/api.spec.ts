import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Api } from './api';

describe('Api', () => {
  let api: Api;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(Api);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia somente os filtros preenchidos na listagem de dispositivos', () => {
    api.devices({ recognized: false, q: '  intelbras ', status: '', page: 2, size: 25, sort: 'lastSeenAt,desc' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/devices');
    expect(req.request.params.get('recognized')).toBe('false');
    expect(req.request.params.get('q')).toBe('intelbras');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.get('sort')).toBe('lastSeenAt,desc');
    req.flush({ content: [], page: 2, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('inicia a descoberta com ou sem faixa', () => {
    api.startDiscovery().subscribe();
    expect(http.expectOne('/api/discovery/runs').request.body).toEqual({});

    api.startDiscovery('192.168.0.0/24').subscribe();
    expect(http.expectOne('/api/discovery/runs').request.body).toEqual({ cidr: '192.168.0.0/24' });
  });

  it('inicia verificação de portas personalizada', () => {
    api.startPortScan('abc', 'CUSTOM', '22,80').subscribe();
    const req = http.expectOne('/api/devices/abc/port-scans');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ type: 'CUSTOM', ports: '22,80' });
  });

  it('filtra portas por situação e número', () => {
    api.ports({ state: 'OPEN', port: 445 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/ports');
    expect(req.request.params.get('state')).toBe('OPEN');
    expect(req.request.params.get('port')).toBe('445');
  });

  it('inicia a captura enviando só o que foi escolhido', () => {
    api.startCapture().subscribe();
    expect(http.expectOne('/api/capture/sessions').request.body).toEqual({});

    api.startCapture('\Device\NPF_{X}', 30).subscribe();
    expect(http.expectOne('/api/capture/sessions').request.body).toEqual({
      interfaceName: '\Device\NPF_{X}',
      durationMinutes: 30,
    });

    api.stopCapture('abc').subscribe();
    expect(http.expectOne('/api/capture/sessions/abc/stop').request.method).toBe('POST');
  });

  it('envia somente os filtros preenchidos na listagem de fluxos', () => {
    api.trafficFlows({ ip: ' 192.168.0.10 ', protocol: '', port: null, window: '24h', page: 1, size: 25 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/traffic/flows');
    expect(req.request.params.get('ip')).toBe('192.168.0.10');
    expect(req.request.params.has('protocol')).toBe(false);
    expect(req.request.params.has('port')).toBe(false);
    expect(req.request.params.get('window')).toBe('24h');
    expect(req.request.params.get('page')).toBe('1');
  });

  it('consulta o resumo e o tráfego do dispositivo pelo período', () => {
    api.trafficSummary('7d').subscribe();
    expect(http.expectOne((r) => r.url === '/api/traffic/summary').request.params.get('window')).toBe('7d');

    api.deviceTraffic('d1').subscribe();
    expect(http.expectOne((r) => r.url === '/api/devices/d1/traffic').request.params.get('window')).toBe('24h');
  });

  it('envia somente os filtros preenchidos no histórico de eventos', () => {
    api.events({ severity: 'WARNING', category: '', type: '', q: ' 192.168.0.10 ', window: '7d', page: 0, size: 25 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/events');
    expect(req.request.params.get('severity')).toBe('WARNING');
    expect(req.request.params.has('category')).toBe(false);
    expect(req.request.params.has('type')).toBe(false);
    expect(req.request.params.get('q')).toBe('192.168.0.10');
    expect(req.request.params.get('window')).toBe('7d');

    api.eventSummary('30d').subscribe();
    expect(http.expectOne((r) => r.url === '/api/events/summary').request.params.get('window')).toBe('30d');

    api.deviceEvents('d1', 2).subscribe();
    expect(http.expectOne((r) => r.url === '/api/devices/d1/events').request.params.get('page')).toBe('2');
  });

  it('salva a regra de segurança e filtra os alertas', () => {
    api.updateSecurityRule('RISKY_SERVICE', { enabled: true, severity: 'HIGH', params: { ports: '22,3389' } }).subscribe();
    const put = http.expectOne('/api/security/rules/RISKY_SERVICE');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.params.ports).toBe('22,3389');

    api.securityAlerts({ severity: 'CRITICAL', status: '', rule: '', page: 1 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/security/alerts');
    expect(req.request.params.get('severity')).toBe('CRITICAL');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('rule')).toBe(false);
    expect(req.request.params.get('page')).toBe('1');
  });

  it('trata alertas: ações individuais, descarte e lote', () => {
    api.acknowledgeAlert('a1').subscribe();
    const ack = http.expectOne('/api/security/alerts/a1/acknowledge');
    expect(ack.request.method).toBe('POST');
    expect(ack.request.body).toEqual({ note: null });

    api.dismissAlert('a1', 'ACCEPTED_RISK', 'compartilhamento necessário', true).subscribe();
    expect(http.expectOne('/api/security/alerts/a1/dismiss').request.body).toEqual({
      reason: 'ACCEPTED_RISK',
      note: 'compartilhamento necessário',
      suppress: true,
    });

    api.bulkAlerts(['a1', 'a2'], 'RESOLVE', { note: 'feito' }).subscribe();
    expect(http.expectOne('/api/security/alerts/bulk').request.body).toEqual({
      ids: ['a1', 'a2'],
      action: 'RESOLVE',
      note: 'feito',
      reason: null,
      suppress: false,
    });

    api.securityAlerts({ state: 'active', q: ' 192.168.0.5 ' }).subscribe();
    const list = http.expectOne((r) => r.url === '/api/security/alerts');
    expect(list.request.params.get('state')).toBe('active');
    expect(list.request.params.get('q')).toBe('192.168.0.5');
  });

  it('emite relatórios e monta os endereços de download', () => {
    api.generateReport({ type: 'SECURITY', period: 'CUSTOM', from: '2026-11-01', to: '2026-11-07' }).subscribe();
    const post = http.expectOne('/api/reports');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ type: 'SECURITY', period: 'CUSTOM', from: '2026-11-01', to: '2026-11-07' });

    api.reports('INVENTORY', 1).subscribe();
    const list = http.expectOne((r) => r.url === '/api/reports');
    expect(list.request.params.get('type')).toBe('INVENTORY');
    expect(list.request.params.get('page')).toBe('1');

    api.reports().subscribe();
    expect(http.expectOne((r) => r.url === '/api/reports').request.params.has('type')).toBe(false);

    expect(api.reportPdfUrl('r1')).toBe('/api/reports/r1/pdf');
    expect(api.reportCsvUrl('r1')).toBe('/api/reports/r1/csv');
    expect(api.reportCsvUrl('r1', 2)).toBe('/api/reports/r1/csv?table=2');
  });

  it('altera e restaura configurações', () => {
    api.updateSettings({ 'capture.mode': 'connections', 'ports.timeout-ms': '3000' }).subscribe();
    const put = http.expectOne('/api/settings');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ 'capture.mode': 'connections', 'ports.timeout-ms': '3000' });

    api.restoreSetting('ports.timeout-ms').subscribe();
    expect(http.expectOne('/api/settings/ports.timeout-ms').request.method).toBe('DELETE');
  });
});
