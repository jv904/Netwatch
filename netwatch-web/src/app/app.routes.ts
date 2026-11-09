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
  { path: '**', redirectTo: 'dashboard' },
];
