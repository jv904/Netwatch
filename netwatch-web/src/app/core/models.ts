/** Tipos espelhando os contratos da NetWatch API. */

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}

// ---------- Dispositivos ----------

export type DeviceStatus = 'ACTIVE' | 'INACTIVE';

export type DeviceType =
  | 'COMPUTER'
  | 'NOTEBOOK'
  | 'SMARTPHONE'
  | 'TABLET'
  | 'PRINTER'
  | 'SERVER'
  | 'ROUTER'
  | 'SWITCH'
  | 'ACCESS_POINT'
  | 'IOT'
  | 'OTHER'
  | 'UNKNOWN';

export interface Device {
  id: string;
  displayName: string;
  ipAddress: string;
  macAddress: string | null;
  hostname: string | null;
  vendor: string | null;
  customName: string | null;
  type: DeviceType;
  status: DeviceStatus;
  recognized: boolean;
  notes: string | null;
  firstSeenAt: string | null;
  lastSeenAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DeviceSummary {
  total: number;
  active: number;
  inactive: number;
  unrecognized: number;
}

export interface DeviceQuery {
  status?: DeviceStatus | '';
  type?: DeviceType | '';
  recognized?: boolean | null;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface UpdateDeviceRequest {
  customName: string | null;
  type: DeviceType;
  recognized: boolean;
  notes: string | null;
}

// ---------- Descoberta ----------

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface AuthorizedNetwork {
  cidr: string;
  source: string;
  hostCount: number;
  virtual: boolean;
}

export interface DiscoveryRun {
  id: string;
  cidr: string;
  status: RunStatus;
  trigger: 'MANUAL' | 'SCHEDULED';
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  hostsScanned: number;
  devicesFound: number;
  newDevices: number;
  inactivated: number;
  errorMessage: string | null;
}

// ---------- Portas ----------

export type PortState = 'OPEN' | 'CLOSED' | 'FILTERED';
export type PortScanType = 'COMMON' | 'CUSTOM';

export interface PortScanResult {
  port: number;
  state: PortState;
  previousState: PortState | null;
  newlyOpened: boolean;
  service: string | null;
}

export interface PortScan {
  id: string;
  deviceId: string;
  ipAddress: string;
  type: PortScanType;
  requestedPorts: string;
  portCount: number;
  status: RunStatus;
  startedAt: string;
  finishedAt?: string;
  durationMs?: number;
  openCount: number;
  closedCount: number;
  filteredCount: number;
  errorMessage?: string;
  results?: PortScanResult[];
}

export interface DevicePort {
  port: number;
  state: PortState;
  service: string | null;
  openSince: string | null;
  lastCheckedAt: string;
  lastStateChangeAt: string;
}

export interface DevicePorts {
  scanning: boolean;
  lastScan: PortScan | null;
  ports: DevicePort[];
}

export interface NetworkPort {
  deviceId: string;
  deviceName: string | null;
  ipAddress: string | null;
  vendor: string | null;
  port: number;
  state: PortState;
  service: string | null;
  openSince: string | null;
  lastCheckedAt: string;
}

export interface ExposedService {
  port: number;
  service: string | null;
  devices: number;
}

// ---------- Dashboard ----------

export interface DashboardOverview {
  devices: DeviceSummary;
  openPorts: number;
  lastDiscovery: DiscoveryRun | null;
  discoveryHistory: { startedAt: string; devicesFound: number; newDevices: number }[];
  topVendors: { vendor: string | null; devices: number }[];
}
