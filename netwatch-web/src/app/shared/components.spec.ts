import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { BarChart, BarItem } from './bar-chart';
import { DeviceStatusChip, PortStateChip } from './chips';
import { KpiCard } from './kpi-card';
import { Paginator } from './paginator';

@Component({
  imports: [DeviceStatusChip, PortStateChip, KpiCard, BarChart, Paginator],
  template: `
    <app-device-status id="unknown" [device]="{ status: 'ACTIVE', recognized: false }" />
    <app-device-status id="known" [device]="{ status: 'ACTIVE', recognized: true }" />
    <app-device-status id="inactive" [device]="{ status: 'INACTIVE', recognized: true }" />
    <app-port-state id="open" state="OPEN" />
    <app-port-state id="filtered" state="FILTERED" />
    <app-kpi-card id="kpi" label="Dispositivos ativos" [value]="value()" caption="3 inativos" />
    <app-bar-chart id="bars" [items]="items" [interactive]="true" (itemSelect)="selected = $event" />
    <app-paginator id="pager" [page]="1" [size]="25" [total]="132" (pageChange)="page = $event" />
  `,
})
class Host {
  value = signal<string | null>(null);
  items: BarItem[] = [
    { label: 'Intelbras', value: 45 },
    { label: 'Dell Inc.', value: 10 },
  ];
  selected?: BarItem;
  page?: number;
}

describe('componentes compartilhados', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('chip de situação segue o manual (Atenção para desconhecidos)', async () => {
    const { el } = await render();
    const unknown = el.querySelector('#unknown .chip')!;
    expect(unknown.textContent?.trim()).toBe('Atenção');
    expect(unknown.classList).toContain('warning');
    expect(el.querySelector('#known .chip')!.textContent?.trim()).toBe('Ativo');
    expect(el.querySelector('#inactive .chip')!.textContent?.trim()).toBe('Inativo');
  });

  it('chip de porta mostra rótulo em português', async () => {
    const { el } = await render();
    expect(el.querySelector('#open .chip')!.textContent?.trim()).toBe('Aberta');
    expect(el.querySelector('#filtered .chip')!.textContent?.trim()).toBe('Sem resposta');
  });

  it('kpi mostra carregamento e depois o valor', async () => {
    const { fixture, el } = await render();
    expect(el.querySelector('#kpi .kpi-value')!.classList).toContain('skeleton');

    fixture.componentInstance.value.set('128');
    await fixture.whenStable();
    expect(el.querySelector('#kpi .kpi-value')!.textContent?.trim()).toBe('128');
    expect(el.querySelector('#kpi .kpi-value')!.classList).not.toContain('skeleton');
  });

  it('barras são proporcionais e clicáveis', async () => {
    const { fixture, el } = await render();
    const bars = el.querySelectorAll<HTMLElement>('#bars .bar');
    expect(bars.length).toBe(2);
    expect(parseFloat(bars[0].style.width)).toBeGreaterThan(parseFloat(bars[1].style.width));

    el.querySelectorAll<HTMLElement>('#bars .bar-row')[1].click();
    expect(fixture.componentInstance.selected?.label).toBe('Dell Inc.');
  });

  it('paginador mostra o intervalo e navega', async () => {
    const { fixture, el } = await render();
    expect(el.querySelector('#pager')!.textContent).toContain('26–50 de 132');

    const next = Array.from(el.querySelectorAll<HTMLButtonElement>('#pager button')).find((b) => b.textContent?.includes('Próxima'))!;
    next.click();
    expect(fixture.componentInstance.page).toBe(2);
  });
});
