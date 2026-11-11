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

// ---------- Tráfego (captura de pacotes) ----------

export type CaptureStatus = 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'FAILED';
export type CaptureMode = 'PACKETS' | 'CONNECTIONS';
export type TrafficProtocol = 'TCP' | 'UDP' | 'ICMP' | 'IGMP' | 'ARP' | 'OTHER';
export type TrafficWindow = '1h' | '24h' | '7d';

export interface CaptureSession {
  id: string;
  mode: CaptureMode;
  interfaceName: string;
  interfaceDescription: string | null;
  localAddress: string | null;
  filter: string;
  status: CaptureStatus;
  startedAt: string;
  plannedEndAt: string;
  finishedAt?: string;
  durationMs?: number;
  packets: number;
  bytes: number;
  droppedPackets: number;
  newDevices: number;
  errorMessage?: string;
}

export interface CaptureState {
  enabled: boolean;
  available: boolean;
  mode: CaptureMode | null;
  packetCaptureAvailable: boolean;
  connectionTableAvailable: boolean;
  reason: string | null;
  libraryVersion: string | null;
  authorizedNetworks: string[];
  maxDurationMinutes: number;
  defaultDurationMinutes: number;
  retentionDays: number;
  activeSession: CaptureSession | null;
  lastSession: CaptureSession | null;
}

export interface CaptureInterface {
  name: string;
  description: string;
  macAddress: string | null;
  addresses: string[];
  eligible: boolean;
  authorizedAddress: string | null;
}

export interface TrafficFlow {
  id: string;
  sessionId: string;
  protocol: TrafficProtocol;
  srcIp: string;
  srcPort: number | null;
  srcDeviceId: string | null;
  srcName: string | null;
  dstIp: string;
  dstPort: number | null;
  dstDeviceId: string | null;
  dstName: string | null;
  servicePort: number | null;
  service: string | null;
  processName: string | null;
  connectionState: string | null;
  packets: number;
  bytes: number;
  tcpFlags: number;
  firstSeen: string;
  lastSeen: string;
}

export interface TrafficSummary {
  window: TrafficWindow;
  since: string;
  until: string;
  bucketSeconds: number;
  totals: { packets: number; bytes: number; flows: number; hosts: number; connections: number };
  series: { at: string; packets: number; bytes: number; connections: number }[];
  protocols: { protocol: TrafficProtocol; packets: number; bytes: number }[];
  topHosts: {
    ip: string;
    deviceId: string | null;
    name: string | null;
    local: boolean;
    bytesSent: number;
    bytesReceived: number;
    packets: number;
    flows: number;
  }[];
  topServices: { protocol: TrafficProtocol; port: number; service: string | null; bytes: number; packets: number; flows: number }[];
}

export interface FlowQuery {
  ip?: string;
  deviceId?: string;
  protocol?: TrafficProtocol | '';
  port?: number | null;
  window?: TrafficWindow;
  page?: number;
  size?: number;
}

export interface DeviceTraffic {
  window: TrafficWindow;
  bytesSent: number;
  bytesReceived: number;
  packetsSent: number;
  packetsReceived: number;
  flows: number;
  peers: { ip: string; deviceId: string | null; name: string | null; bytes: number; packets: number; flows: number }[];
  recentFlows: TrafficFlow[];
}

// ---------- Atividades (histórico de eventos) ----------

export type EventSeverity = 'INFO' | 'NOTICE' | 'WARNING';
export type EventCategory = 'DEVICE' | 'PORT' | 'DISCOVERY' | 'TRAFFIC';
export type EventType =
  | 'DEVICE_DISCOVERED'
  | 'DEVICE_IP_CHANGED'
  | 'DEVICE_RETURNED'
  | 'DEVICE_INACTIVE'
  | 'DEVICE_CREATED'
  | 'DEVICE_RECOGNIZED'
  | 'DEVICE_UNRECOGNIZED'
  | 'DEVICE_DELETED'
  | 'PORT_OPENED'
  | 'PORT_CLOSED'
  | 'PORT_SCAN_COMPLETED'
  | 'PORT_SCAN_FAILED'
  | 'DISCOVERY_COMPLETED'
  | 'DISCOVERY_FAILED'
  | 'CAPTURE_STARTED'
  | 'CAPTURE_FINISHED'
  | 'CAPTURE_FAILED'
  | 'TRAFFIC_NEW_SERVICE';
export type ActivityWindow = '24h' | '7d' | '30d';

export interface NetworkEvent {
  id: string;
  occurredAt: string;
  type: EventType;
  category: EventCategory;
  severity: EventSeverity;
  title: string;
  details: string | null;
  deviceId: string | null;
  deviceName: string | null;
  ipAddress: string | null;
  referenceId: string | null;
}

export interface EventQuery {
  type?: EventType | '';
  category?: EventCategory | '';
  severity?: EventSeverity | '';
  deviceId?: string;
  q?: string;
  window?: ActivityWindow;
  page?: number;
  size?: number;
}

export interface ActivitySummary {
  window: ActivityWindow;
  since: string;
  until: string;
  bucketSeconds: number;
  timeZone: string;
  totals: {
    total: number;
    warnings: number;
    notices: number;
    info: number;
    newDevices: number;
    portsOpened: number;
    newServices: number;
  };
  series: { at: string; total: number; warnings: number }[];
  byType: { type: EventType; category: EventCategory; severity: EventSeverity; count: number }[];
  byHour: { hour: number; count: number }[];
  topDevices: { deviceId: string; name: string; ipAddress: string; count: number; warnings: number }[];
}
