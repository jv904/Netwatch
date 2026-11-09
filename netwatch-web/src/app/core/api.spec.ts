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
});
