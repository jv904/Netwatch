import { Injectable, signal } from '@angular/core';

/** Avisa as telas quando uma descoberta é iniciada em outro lugar (ex.: botão da barra superior). */
@Injectable({ providedIn: 'root' })
export class DiscoveryEvents {
  readonly started = signal(0);

  notifyStarted(): void {
    this.started.update((n) => n + 1);
  }
}
