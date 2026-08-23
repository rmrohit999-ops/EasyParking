import { CallHandler, ExecutionContext, Injectable, Logger, NestInterceptor } from '@nestjs/common';
import { Request, Response } from 'express';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { CORRELATION_ID_REQUEST_KEY } from '../logging/correlation-id.middleware';

/**
 * Structured request-completion log with latency. Kept intentionally
 * separate from nestjs-pino's own HTTP logger so we can attach
 * business-relevant fields (route, status, duration, correlationId) in one
 * predictable shape for the API-latency metrics called for in Milestone 0.
 */
@Injectable()
export class LoggingInterceptor implements NestInterceptor {
  private readonly logger = new Logger('HTTP');

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<Request>();
    const response = context.switchToHttp().getResponse<Response>();
    const start = Date.now();
    const correlationId = (request as Request & { [CORRELATION_ID_REQUEST_KEY]?: string })[
      CORRELATION_ID_REQUEST_KEY
    ];

    return next.handle().pipe(
      tap({
        next: () => {
          this.logger.log(
            `${request.method} ${request.originalUrl} ${response.statusCode} ${Date.now() - start}ms [${correlationId}]`,
          );
        },
        error: () => {
          this.logger.warn(
            `${request.method} ${request.originalUrl} ERROR ${Date.now() - start}ms [${correlationId}]`,
          );
        },
      }),
    );
  }
}
