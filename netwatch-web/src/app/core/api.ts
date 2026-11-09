import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  AuthorizedNetwork,
  DashboardOverview,
  Device,
  DevicePorts,
  DeviceQuery,
  DiscoveryRun,
  ExposedService,
  NetworkPort,
  Page,
  PortScan,
  PortScanType,
  PortState,
  UpdateDeviceRequest,
} from './models';

/** Acesso à NetWatch API (em desenvolvimento, `/api` é encaminhado para o backend pelo proxy). */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  // ---------- Dashboard ----------

  overview(): Observable<DashboardOverview> {
    return this.http.get<DashboardOverview>('/api/dashboard/overview');
  }

  health(): Observable<boolean> {
    return this.http.get<{ status: string }>('/actuator/health').pipe(map((h) => h.status === 'UP'));
  }

  // ---------- Dispositivos ----------

  devices(query: DeviceQuery = {}): Observable<Page<Device>> {
    let params = new HttpParams();
    if (query.status) params = params.set('status', query.status);
    if (query.type) params = params.set('type', query.type);
    if (query.recognized !== undefined && query.recognized !== null) {
      params = params.set('recognized', String(query.recognized));
    }
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    params = params.set('page', String(query.page ?? 0)).set('size', String(query.size ?? 20));
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<Device>>('/api/devices', { params });
  }

  device(id: string): Observable<Device> {
    return this.http.get<Device>(`/api/devices/${id}`);
  }

  updateDevice(id: string, body: UpdateDeviceRequest): Observable<Device> {
    return this.http.put<Device>(`/api/devices/${id}`, body);
  }

  setRecognized(id: string, recognized: boolean): Observable<Device> {
    return this.http.patch<Device>(`/api/devices/${id}/recognition`, { recognized });
  }

  deleteDevice(id: string): Observable<void> {
    return this.http.delete<void>(`/api/devices/${id}`);
  }

  // ---------- Descoberta ----------

  networks(): Observable<AuthorizedNetwork[]> {
    return this.http.get<AuthorizedNetwork[]>('/api/discovery/networks');
  }

  startDiscovery(cidr?: string): Observable<DiscoveryRun> {
    return this.http.post<DiscoveryRun>('/api/discovery/runs', cidr ? { cidr } : {});
  }

  currentDiscovery(): Observable<DiscoveryRun | null> {
    return this.http.get<DiscoveryRun | null>('/api/discovery/runs/current');
  }

  discoveryRuns(page = 0, size = 10): Observable<Page<DiscoveryRun>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<DiscoveryRun>>('/api/discovery/runs', { params });
  }

  // ---------- Portas ----------

  devicePorts(deviceId: string): Observable<DevicePorts> {
    return this.http.get<DevicePorts>(`/api/devices/${deviceId}/ports`);
  }

  startPortScan(deviceId: string, type: PortScanType, ports?: string): Observable<PortScan> {
    return this.http.post<PortScan>(`/api/devices/${deviceId}/port-scans`, { type, ports });
  }

  portScan(scanId: string): Observable<PortScan> {
    return this.http.get<PortScan>(`/api/port-scans/${scanId}`);
  }

  ports(query: {
    state?: PortState | '';
    port?: number | null;
    q?: string;
    page?: number;
    size?: number;
  }): Observable<Page<NetworkPort>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 50);
    if (query.state) params = params.set('state', query.state);
    if (query.port) params = params.set('port', query.port);
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    return this.http.get<Page<NetworkPort>>('/api/ports', { params });
  }

  exposedServices(): Observable<ExposedService[]> {
    return this.http.get<ExposedService[]>('/api/ports/services');
  }
}
