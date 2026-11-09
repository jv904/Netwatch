import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-paginator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > 0) {
      <div class="pager">
        <span class="muted nums">{{ from() }}–{{ to() }} de {{ total().toLocaleString('pt-BR') }}</span>
        <span class="spacer"></span>
        <button class="btn btn-sm" type="button" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">
          Anterior
        </button>
        <span class="muted nums">{{ page() + 1 }} / {{ pages() }}</span>
        <button class="btn btn-sm" type="button" [disabled]="page() + 1 >= pages()" (click)="pageChange.emit(page() + 1)">
          Próxima
        </button>
      </div>
    }
  `,
  styles: `
    .pager { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-top: 1px solid var(--border); }
  `,
})
export class Paginator {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));
  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));
}
