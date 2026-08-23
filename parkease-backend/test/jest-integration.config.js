/**
 * Integration tests: boot the real Nest application against a real
 * Postgres + Redis (via docker-compose). Not run by default `npm test`
 * (kept out of CI's fast path) — run explicitly with `npm run test:integration`
 * after `docker compose up -d db redis` and `prisma migrate deploy`.
 */
module.exports = {
  rootDir: '../',
  testEnvironment: 'node',
  moduleFileExtensions: ['js', 'json', 'ts'],
  testRegex: 'test/integration/.*\\.e2e-spec\\.ts$',
  transform: {
    '^.+\\.(t|j)s$': 'ts-jest',
  },
  testTimeout: 30000,
};
