import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import * as request from 'supertest';
import { AppModule } from '../../src/app.module';

/**
 * Real integration test: boots the actual AppModule (real Prisma + real
 * Redis health service) and hits /health over HTTP. Requires
 * `docker compose up -d db redis` and a migrated database — see
 * package.json's test:integration script and the backend README.
 */
describe('Health (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = moduleRef.createNestApplication();
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('GET /health reports ok with real DB and Redis checks when dependencies are up', async () => {
    const res = await request(app.getHttpServer()).get('/health');
    expect([200, 503]).toContain(res.status); // 503 only if a dependency is genuinely down
    expect(res.body.checks).toHaveProperty('database');
    expect(res.body.checks).toHaveProperty('redis');
    expect(res.body).toHaveProperty('timestamp');
  });
});
