import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Request, Response } from 'express';
import { CORRELATION_ID_REQUEST_KEY } from '../logging/correlation-id.middleware';

/**
 * Global exception filter. Two hard rules from the Milestone 0 spec live
 * here: never leak a raw stack trace / internal error code to the client,
 * and always return a stable, typed error shape the Android client can
 * branch on. Unexpected (non-HttpException) errors are logged with full
 * detail server-side but returned to the client as a generic message.
 */
@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger('ExceptionFilter');

  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();
    const correlationId = (request as Request & { [CORRELATION_ID_REQUEST_KEY]?: string })[
      CORRELATION_ID_REQUEST_KEY
    ];

    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let code = 'INTERNAL_ERROR';
    let message = "We couldn't complete that right now. Please try again.";
    let details: unknown;

    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const body = exception.getResponse();
      if (typeof body === 'string') {
        message = body;
      } else if (typeof body === 'object' && body !== null) {
        const b = body as Record<string, unknown>;
        message = typeof b.message === 'string' ? b.message : message;
        details = Array.isArray(b.message) ? b.message : undefined;
      }
      code = HttpStatus[status] ?? 'HTTP_ERROR';
    } else {
      this.logger.error(
        `Unhandled exception [${correlationId}]: ${(exception as Error)?.message}`,
        (exception as Error)?.stack,
      );
    }

    response.status(status).json({
      error: {
        code,
        message,
        details,
        correlationId,
        timestamp: new Date().toISOString(),
        path: request.url,
      },
    });
  }
}
