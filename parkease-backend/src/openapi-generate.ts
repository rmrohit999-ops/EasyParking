import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';
import { AppModule } from './app.module';

/**
 * Generates openapi.yaml from the live controller/DTO decorators without
 * starting an HTTP listener — run via `npm run openapi:generate`, and in
 * CI, to keep the committed spec honest against the actual code rather than
 * hand-maintained and drifting.
 */
async function generate() {
  const app = await NestFactory.create(AppModule, { logger: false });

  const config = new DocumentBuilder()
    .setTitle('ParkEase API')
    .setDescription('ParkEase — smart parking marketplace API.')
    .setVersion('0.1.0')
    .addBearerAuth()
    .build();

  const document = SwaggerModule.createDocument(app, config);
  const outPath = path.resolve(__dirname, '..', 'openapi.yaml');
  fs.writeFileSync(outPath, yaml.dump(document, { noRefs: true }));
  // eslint-disable-next-line no-console
  console.log(`OpenAPI spec written to ${outPath}`);
  await app.close();
}

generate().catch((err) => {
  // eslint-disable-next-line no-console
  console.error('Failed to generate OpenAPI spec:', err);
  process.exit(1);
});
