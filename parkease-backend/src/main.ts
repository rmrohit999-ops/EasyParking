import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { ValidationPipe, VersioningType } from '@nestjs/common';
import { Logger } from 'nestjs-pino';
import helmet from 'helmet';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { GlobalExceptionFilter } from './common/filters/http-exception.filter';
import { LoggingInterceptor } from './common/interceptors/logging.interceptor';
import { AppConfig } from './common/config/configuration';

async function bootstrap() {
  // rawBody: true keeps Nest's normal JSON body parsing (req.body) but
  // additionally stashes the exact, unparsed bytes on req.rawBody —
  // Milestone 7's payment webhook signature verification (HMAC over the
  // raw payload) must run against those exact bytes, not a re-serialized
  // JSON.stringify(req.body), since re-serialization is not guaranteed to
  // byte-for-byte match what the gateway actually signed.
  const app = await NestFactory.create(AppModule, { bufferLogs: true, rawBody: true });

  const logger = app.get(Logger);
  app.useLogger(logger);

  const configService = app.get(ConfigService<AppConfig, true>);

  app.use(helmet());

  // Milestone 12 (release hardening) fix: this used to fall back to
  // `origin: true` (reflect-any-origin) whenever CORS_ALLOWED_ORIGINS was
  // unset — combined with `credentials: true`, that let ANY website make
  // credentialed cross-origin requests against this API, which is exactly
  // the CORS misconfiguration a security review exists to catch. The
  // Android app itself was never affected (CORS is a browser-enforced
  // mechanism; native HTTP clients ignore it entirely), so the only real
  // consumer of an open CORS policy here would be a browser-based client —
  // and until a real one exists, "closed by default" is the safe choice.
  // `origin: true` now only applies in `development`, where it's a real
  // convenience (arbitrary localhost ports for local web tooling) rather
  // than an accidental production exposure; every other environment
  // defaults to fully closed (no cross-origin browser access at all) if
  // CORS_ALLOWED_ORIGINS isn't explicitly set, with a loud startup warning
  // so a misconfiguration is visible immediately rather than discovered
  // during a security review.
  const corsAllowedOrigins = configService.get('corsAllowedOrigins', { infer: true });
  const env = configService.get('env', { infer: true });
  let corsOrigin: string[] | boolean;
  if (corsAllowedOrigins.length) {
    corsOrigin = corsAllowedOrigins;
  } else if (env === 'development') {
    corsOrigin = true;
  } else {
    corsOrigin = false;
    logger.warn(
      `CORS_ALLOWED_ORIGINS is not set in env=${env} — cross-origin browser requests to this API ` +
        'are fully disabled until it is configured. Native app clients (Android) are unaffected.',
    );
  }
  app.enableCors({ origin: corsOrigin, credentials: true });

  app.enableVersioning({ type: VersioningType.URI, defaultVersion: '1' });

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: true },
    }),
  );
  app.useGlobalFilters(new GlobalExceptionFilter());
  app.useGlobalInterceptors(new LoggingInterceptor());

  // Milestone 12 (release hardening): gated per AppConfig.enableApiDocs —
  // see its doc comment in configuration.ts for the on-by-default-outside-
  // production / off-by-default-in-production reasoning.
  if (configService.get('enableApiDocs', { infer: true })) {
    const swaggerConfig = new DocumentBuilder()
      .setTitle('ParkEase API')
      .setDescription(
        'ParkEase — smart parking marketplace API. See docs/00_ARCHITECTURE for the full ' +
          'system design; this document is generated from the live controller/DTO decorators.',
      )
      .setVersion('0.1.0')
      .addBearerAuth()
      .build();
    const document = SwaggerModule.createDocument(app, swaggerConfig);
    SwaggerModule.setup('docs', app, document);
  } else {
    logger.log('API docs (/docs) are disabled — set ENABLE_API_DOCS=true to turn them on.');
  }

  const port = configService.get('port', { infer: true });
  await app.listen(port, '0.0.0.0');
  logger.log(`ParkEase backend listening on port ${port} (env=${configService.get('env', { infer: true })})`);
}

bootstrap();
