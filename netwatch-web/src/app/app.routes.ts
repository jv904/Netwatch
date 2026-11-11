import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Dashboard · NetWatch',
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'devices',
    title: 'Dispositivos · NetWatch',
    loadComponent: () => import('./features/devices/device-list').then((m) => m.DeviceList),
  },
  {
    path: 'devices/:id',
    title: 'Dispositivo · NetWatch',
    loadComponent: () => import('./features/devices/device-detail').then((m) => m.DeviceDetail),
  },
  {
    path: 'discovery',
    title: 'Descoberta · NetWatch',
    loadComponent: () => import('./features/discovery/discovery').then((m) => m.Discovery),
  },
  {
    path: 'ports',
    title: 'Portas e serviços · NetWatch',
    loadComponent: () => import('./features/ports/ports').then((m) => m.Ports),
  },
  {
    path: 'traffic',
    title: 'Tráfego · NetWatch',
    loadComponent: () => import('./features/traffic/traffic').then((m) => m.Traffic),
  },
  {
    path: 'activity',
    title: 'Atividades · NetWatch',
    loadComponent: () => import('./features/activity/activity').then((m) => m.Activity),
  },
  {
    path: 'security',
    title: 'Segurança · NetWatch',
    loadComponent: () => import('./features/security/security').then((m) => m.Security),
  },
  {
    path: 'alerts',
    title: 'Alertas · NetWatch',
    loadComponent: () => import('./features/alerts/alerts').then((m) => m.Alerts),
  },
  { path: '**', redirectTo: 'dashboard' },
];
