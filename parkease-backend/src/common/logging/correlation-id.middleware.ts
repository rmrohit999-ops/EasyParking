import { Injectable, NestMiddleware } from '@nestjs/common';
import { NextFunction, Request, Response } from 'express';
import { randomUUID } from 'crypto';

export const CORRELATION_ID_REQUEST_KEY = 'correlationId';

/**
 * Ensures every request carries a correlation ID: reuses one supplied by the
 * client/upstream proxy (x-correlation-id) or mints a new UUID. Echoed back
 * on the response and attached to req so the logging interceptor, the
 * exception filter, and (from Milestone 7 on) webhook/audit logging can all
 * tie a single request's log lines together.
 */
@Injectable()
export class CorrelationIdMiddleware implements NestMiddleware {
  use(req: Request, res: Response, next: NextFunction) {
    const headerName = 'x-correlation-id';
    const incoming = req.header(headerName);
    const correlationId = incoming && incoming.length > 0 ? incoming : randomUUID();
    (req as Request & { [CORRELATION_ID_REQUEST_KEY]?: string })[CORRELATION_ID_REQUEST_KEY] = correlationId;
    res.setHeader(headerName, correlationId);
    next();
  }
}
