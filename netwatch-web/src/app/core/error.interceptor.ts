import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { ProblemDetail } from './models';
import { ToastService } from './toast';

/** Converte erros da API (Problem Details) em avisos legíveis. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !req.url.startsWith('/actuator')) {
        toast.error(describeError(error));
      }
      return throwError(() => error);
    }),
  );
};

export function describeError(error: HttpErrorResponse): string {
  if (error.status === 0 || error.status === 502 || error.status === 504) {
    return 'Não foi possível conectar à API. Verifique se o backend está em execução.';
  }
  const problem = error.error as ProblemDetail | null;
  if (problem?.errors) {
    const first = Object.values(problem.errors)[0];
    if (first) return first;
  }
  return problem?.detail ?? problem?.title ?? `Erro inesperado (${error.status}).`;
}
