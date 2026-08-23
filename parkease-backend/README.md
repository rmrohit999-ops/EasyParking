# ParkEase backend

NestJS + Prisma + PostgreSQL/PostGIS + Redis. See `../ParkEase Architecture Blueprint.md`
(Milestone 0) for the full system design this implements.

## Local development

```bash
cp .env.example .env.local
npm install
docker compose up -d db redis minio mailhog
npx prisma migrate dev
npm run start:dev
```

- API: http://localhost:3000
- Swagger/OpenAPI UI: http://localhost:3000/docs
- Health check: http://localhost:3000/health
- MinIO console: http://localhost:9001 (parkease / parkease123)
- Mailhog UI: http://localhost:8025

## Tests

```bash
npm test                  # unit tests — no external dependencies required
npm run test:integration  # boots the real app against docker-composed Postgres/Redis
```

## Database

```bash
npx prisma migrate dev      # create + apply a migration from schema.prisma changes
npx prisma studio           # browse data
npm run prisma:seed         # dev-only seed data (refuses to run with NODE_ENV=production)
```

`prisma/schema.prisma` implements the full Milestone 0 entity model. The PostGIS
`geography` columns (`parking_locations.geog`, `service_areas.boundary`) are modelled as
`Unsupported(...)` fields — Prisma can create/migrate the column but geospatial queries
against them are written as raw SQL (`prisma.$queryRaw`) starting in Milestone 5. The
GiST index on `parking_locations.geog` is added by hand in the generated migration SQL
(search for `-- MANUAL:` in `prisma/migrations/*/migration.sql` after the first
`prisma migrate dev`).

## OpenAPI

```bash
npm run openapi:generate   # writes openapi.yaml from live controller/DTO decorators
```

## What's real vs. scaffolded in this milestone

Real and tested: config validation, structured logging, correlation IDs, global error
handling, the `/health` endpoint (genuine DB + Redis round-trip), the `Money` value type
(integer minor units, deterministic rounding — see boundary-case tests), and the full
Prisma schema/migration.

Scaffolded only (empty NestJS modules, wired into `AppModule`, no business logic yet):
`auth`, `users`, `vehicles`, `parking`, `availability`, `booking`, `payments`, `ledger`,
`settlements`, `qr`, `notifications`, `fraud`, `reviews`, `support`, `disputes`, `admin`,
`reports`, `audit`, `config`, `storage`. Each module file states which milestone fills it in.
