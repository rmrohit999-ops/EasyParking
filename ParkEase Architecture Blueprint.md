# ParkEase — Milestone 0: Architecture & Planning

**Tagline:** "Find Parking. Park Easy." · **Market:** India (INR) · **Status:** Planning only — no code has been written yet.

This document is the single deliverable for Milestone 0. It intentionally contains no application code. Everything below is the contract that Milestones 1–13 will implement against. Where the spec asked for a decision and no single correct answer exists, I picked the option that best serves "real, production-ready, no fakes" and flagged it in **§18 Open Questions** so you can override it before Milestone 1 starts.

---

## 1. Technology Stack

### 1.1 Android client

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Required by spec; only realistic choice for a modern Play Store app. |
| UI | Jetpack Compose + Material 3 | Declarative UI, first-class support for dynamic role-based screens (driver/owner/attendant/admin-lite), faster to keep accessible/responsive than XML views. |
| Navigation | Navigation Compose, multi-graph (one nav graph per role, gated behind a role-aware root) | Prevents a driver from ever navigating into an owner/attendant graph even if a deep link is crafted manually. |
| DI | Hilt | Standard for Compose + WorkManager + Retrofit wiring; scopes session-dependent dependencies (auth token holder) cleanly. |
| Async | Kotlin Coroutines + Flow | Structured concurrency, cancellable searches, StateFlow-driven UI state. |
| Networking | Retrofit + OkHttp (+ Moshi) | Mature, interceptor-friendly (auth headers, correlation IDs, certificate pinning). |
| Local persistence | Room | Offline cache for read-mostly data (favorites, recent bookings, vehicle list) — never the source of truth for availability/pricing/payment state. |
| Background work | WorkManager | QR pass pre-fetch, notification-token sync, retryable receipt downloads — never for anything financially authoritative (that always round-trips the backend). |
| Maps/GPS | Google Maps SDK for Android + Fused Location Provider (Play Services Location) | Required "real map provider"; graceful "map not configured" state if `MAPS_API_KEY` is absent. |
| QR | CameraX + ML Kit Barcode Scanning (or ZXing as fallback) | Maintained, on-device, no network dependency for the scan itself (validation still calls the backend). |
| Push | Firebase Cloud Messaging | De facto standard on Android; abstracted behind a `PushProvider` interface so it can be swapped. |
| Crash/analytics | Firebase Crashlytics + Firebase Analytics (consent-gated) | Crash reporting is non-negotiable for production; analytics collection is opt-in and gated by a consent flag stored server-side. |
| Build | Gradle Kotlin DSL, product flavors `dev` / `staging` / `prod`, App Bundle output | Matches the 3-environment requirement and Play's AAB requirement. |

### 1.2 Backend

| Layer | Choice | Why |
|---|---|---|
| Language | TypeScript | Spec default. |
| Framework | NestJS (modular monolith at launch, module boundaries drawn so services can be extracted later) | A true microservice mesh is unjustified complexity for a first launch and multiplies the transactional-integrity risk around bookings/payments. Modules: `auth`, `users`, `vehicles`, `parking`, `availability`, `booking`, `payments`, `ledger`, `settlements`, `qr`, `notifications`, `fraud`, `support`, `disputes`, `admin`, `reports`, `audit`, `config`. |
| Database | PostgreSQL 15+ | Relational integrity for money/availability, native `PostGIS` extension for geospatial queries, row-level locking (`SELECT … FOR UPDATE`) for capacity control. |
| ORM/migrations | Prisma | Type-safe client, first-class migration history, good fit with NestJS. |
| Geospatial | PostGIS (`geography(Point,4326)` columns + GiST index) | Required for "nearby parking" radius queries; avoids inaccurate Haversine-in-app-code approximations at scale. |
| Cache/locks/queues | Redis | Booking-hold TTLs, distributed locks for capacity mutation, idempotency-key store, BullMQ job queues (webhooks, notifications, settlement jobs). |
| Object storage | S3-compatible (AWS S3 or equivalent) with private buckets + signed URLs | KYC docs and parking photos; public photos served via CDN-fronted public bucket, private docs never public. |
| Queue/jobs | BullMQ on Redis | Webhook processing, notification fan-out, booking-hold expiry, settlement batches — all retryable/idempotent. |
| API docs | OpenAPI via `@nestjs/swagger`, generated at build time | Contract for Android client + reviewer documentation. |
| Logging | Pino (structured JSON) + correlation-ID middleware | Machine-parseable logs, redaction hooks for PII/secrets. |
| Local dev | Docker Compose (Postgres, Redis, MinIO for S3-compatible local storage, backend, mailhog for email testing) | One-command local environment. |

### 1.3 Infrastructure

| Concern | Approach |
|---|---|
| Environments | `dev`, `staging`, `prod` — separate DBs, separate payment-provider accounts (test vs live keys), separate storage buckets, separate FCM projects, separate signing configs. |
| CI/CD | GitHub Actions (or equivalent): lint → typecheck → unit tests → integration tests (Dockerized Postgres/Redis) → build → (on tag) deploy. Android: lint → unit tests → instrumented tests on emulator → assemble/bundle. |
| Secrets | Cloud secret manager (AWS Secrets Manager / GCP Secret Manager / Doppler) injected as env vars at deploy time; never committed; local dev uses `.env.local` (gitignored) with dummy values. |
| Hosting | Containerized backend (ECS/Fargate, Cloud Run, or Render/Railway for early stage) behind a load balancer with TLS termination; managed Postgres (RDS/Cloud SQL) with automated backups + PITR. |
| Monitoring | Sentry (backend + Android crash/error), Prometheus/Grafana or hosted equivalent (Datadog) for latency/throughput, uptime checks (Pingdom/UptimeRobot) on health endpoints. |
| Dependency scanning | `npm audit` / Snyk / GitHub Dependabot for backend; Gradle dependency-check + Dependabot for Android. |

If any of the above is later swapped (e.g., a different cloud), the module boundaries above (esp. `payments`, `notifications`, storage access) are designed as interfaces precisely so the swap doesn't ripple into booking/pricing logic.

---

## 2. System Architecture

```
                                   ┌─────────────────────────┐
                                   │      Android App        │
                                   │  (Driver / Owner /       │
                                   │   Attendant UI graphs)   │
                                   └────────────┬─────────────┘
                                                │ HTTPS + JWT (access+refresh)
                                                ▼
                                   ┌─────────────────────────┐
                                   │   API Gateway / LB       │
                                   │  (TLS, rate limiting)    │
                                   └────────────┬─────────────┘
                                                ▼
                        ┌───────────────────────────────────────────┐
                        │              NestJS Backend                │
                        │  auth · users · vehicles · parking ·       │
                        │  availability · booking · payments ·       │
                        │  ledger · settlements · qr · notifications │
                        │  · fraud · support · disputes · admin ·    │
                        │  reports · audit · config                  │
                        └───┬───────────┬───────────┬───────────┬───┘
                            │           │           │           │
                 ┌──────────▼──┐  ┌─────▼─────┐ ┌───▼────┐ ┌────▼─────┐
                 │ PostgreSQL  │  │   Redis    │ │ Object │ │  BullMQ  │
                 │ + PostGIS   │  │(cache/lock/│ │Storage │ │  Queues  │
                 │ (system of  │  │ idempotency│ │(S3-cmp)│ │(webhooks,│
                 │  record)    │  │ /holds)    │ │        │ │notif,jobs)│
                 └─────────────┘  └────────────┘ └────────┘ └────┬─────┘
                                                                   │
                     ┌─────────────────────────────────────────────┼──────────┐
                     ▼                     ▼                       ▼          ▼
             ┌───────────────┐   ┌──────────────────┐   ┌──────────────┐ ┌─────────┐
             │ Payment        │   │ FCM (push)        │   │ SMS/Email    │ │ Google  │
             │ Gateway        │   │                    │   │ provider     │ │ Maps    │
             │ (Razorpay-class,│   │                    │   │ (transactional│ │ Platform│
             │ webhook +      │   │                    │   │ templates)   │ │ (Geocode/│
             │ order/refund   │   │                    │   │              │ │ Places) │
             │ API)           │   └────────────────────┘   └──────────────┘ └─────────┘
             └───────────────┘
```

**Key architectural rules (non-negotiable, enforced in every milestone):**

1. **Server-authoritative everything.** Availability, price, category compatibility, booking state, and payment state are computed and persisted by the backend inside a transaction. The Android client only ever *displays* what the server returns; it never computes a bookable price or decrements a counter locally.
2. **Capacity mutation = transactional + locked.** Every operation that changes reserved/occupied/blocked counts for a `(parking_section_id, vehicle_category)` pair runs inside a Postgres transaction using `SELECT … FOR UPDATE` on the section-availability row (or an equivalent Redis distributed lock backed by the same invariant), so two concurrent bookings can never both succeed past capacity.
3. **Idempotency everywhere money moves.** Payment order creation, webhook processing, refund issuance, and settlement processing all require an idempotency key; replays return the original result rather than re-executing.
4. **Ledger is append-only.** Financial state changes are new ledger rows (or adjustment rows), never in-place UPDATEs to historical amounts.
5. **Category is a first-class column, not a UI label.** `vehicle_category` appears on section, space, availability, booking, payment, ledger, and analytics tables and is part of every relevant unique/check constraint.
6. **No client-trusted money or availability fields.** Any request body field named `price`, `amount`, `available`, `category` etc. that the client sends is treated as a *hint* for UX only; the backend re-derives the authoritative value server-side before committing.

---

## 3. Project Folder Structure

### 3.1 Android (`/app`)

```
parkease-android/
├── app/                                  # Application module (DI graph root, nav host)
│   └── src/main/java/com/parkease/app/
│       ├── ParkEaseApplication.kt
│       ├── di/                           # Hilt app-level modules
│       └── navigation/                   # Root nav graph, role-gated graph selection
├── core/
│   ├── core-ui/                          # Design system: color tokens, type scale, components
│   ├── core-network/                     # Retrofit setup, interceptors, auth token refresh
│   ├── core-database/                    # Room DB, DAOs (cache only, non-authoritative)
│   ├── core-datastore/                   # Encrypted DataStore: session, consent flags
│   ├── core-location/                    # FusedLocationProviderClient wrapper, permission flow
│   ├── core-analytics/                   # Consent-gated analytics facade
│   └── core-model/                       # Shared Kotlin data classes / enums (VehicleCategory, BookingStatus…)
├── feature/
│   ├── auth/                             # Login, OTP, register, forgot/change password, Google Sign-In
│   ├── driver-home/
│   ├── driver-search/                    # Map + list discovery, filters
│   ├── driver-parking-details/
│   ├── driver-booking/                   # Advance booking + Instant Parking flows
│   ├── driver-payment/
│   ├── driver-pass-qr/                   # Digital pass display
│   ├── driver-bookings/                  # Booking history/status
│   ├── driver-favorites/
│   ├── driver-reviews/
│   ├── owner-onboarding/                 # Verification, KYC, bank/UPI
│   ├── owner-parking-management/         # Create/edit parking, sections, spaces, photos, location
│   ├── owner-availability/               # Live availability, Instant Mode toggles
│   ├── owner-bookings/
│   ├── owner-earnings/                   # Earnings dashboard, settlements
│   ├── owner-analytics/
│   ├── attendant-dashboard/
│   ├── attendant-qr-scan/
│   ├── attendant-incident/
│   ├── admin-lite/                       # Only if a thin native admin surface is needed; full admin is web (see §16)
│   ├── notifications/
│   ├── support/                          # FAQ, tickets, disputes
│   └── settings-profile/
├── build-logic/                          # Convention Gradle plugins (flavor/signing config reuse)
└── gradle/libs.versions.toml             # Version catalog
```

Each `feature/*` module depends only on `core/*` — never on another `feature/*` module — enforced by Gradle module visibility, which is what makes "a driver can't reach an owner screen" true at the build level as well as the navigation-graph level.

### 3.2 Backend (`/server`)

```
parkease-backend/
├── prisma/
│   ├── schema.prisma
│   ├── migrations/
│   └── seed/                             # Dev-only seed script, clearly separated (guarded by NODE_ENV check)
├── src/
│   ├── main.ts
│   ├── app.module.ts
│   ├── common/
│   │   ├── decorators/                   # @Roles(), @CurrentUser(), @Idempotent()
│   │   ├── guards/                       # JwtAuthGuard, RolesGuard, ResourceOwnershipGuard
│   │   ├── interceptors/                 # CorrelationIdInterceptor, LoggingInterceptor
│   │   ├── filters/                      # Global exception filter (never leak stack traces)
│   │   ├── pipes/                        # Validation pipes (class-validator DTOs)
│   │   └── money/                        # Integer-minor-unit Money type + rounding utilities
│   ├── modules/
│   │   ├── auth/
│   │   ├── users/
│   │   ├── vehicles/
│   │   ├── parking/                      # listings, sections, spaces, photos, location, approval
│   │   ├── availability/                 # capacity, holds, occupancy
│   │   ├── booking/                      # lifecycle, state machine, instant parking
│   │   ├── payments/
│   │   │   ├── provider/                 # PaymentProvider interface + RazorpayProvider (or chosen) impl
│   │   │   └── webhooks/
│   │   ├── ledger/
│   │   ├── settlements/
│   │   ├── qr/
│   │   ├── notifications/
│   │   │   └── provider/                 # PushProvider, SmsProvider, EmailProvider interfaces
│   │   ├── fraud/
│   │   ├── reviews/
│   │   ├── support/
│   │   ├── disputes/
│   │   ├── admin/
│   │   ├── reports/
│   │   ├── audit/
│   │   ├── config/                       # commission/tax/feature-flag configuration
│   │   └── storage/                      # Upload/signed-URL abstraction over S3-compatible storage
│   └── jobs/                             # BullMQ processors (hold-expiry, webhook-retry, settlement-batch)
├── test/
│   ├── unit/
│   ├── integration/                      # Dockerized Postgres/Redis
│   └── e2e/
├── docker-compose.yml
├── openapi.yaml                          # Generated
└── .env.example
```

---

## 4. Role & Permission Matrix

Roles: **DRIVER**, **OWNER**, **ATTENDANT**, **ADMIN**. A single user account may hold multiple roles (e.g., a person who is both a driver and an owner) — modeled as a `user_roles` join table, not a single `role` column, specifically so this is possible and testable.

Legend: ✅ full access · 🟡 scoped/conditional · ⛔ no access

| Capability | DRIVER | OWNER | ATTENDANT | ADMIN |
|---|---|---|---|---|
| Register/login/manage own profile | ✅ | ✅ | 🟡 created by Admin/Owner, self-manages profile only | ✅ |
| Manage own vehicles | ✅ | ⛔ | ⛔ | 🟡 read-only support access |
| Search/browse parking | ✅ | 🟡 own listings only, via management UI | ⛔ | ✅ |
| Create/edit parking listing | ⛔ | 🟡 own listings only | ⛔ | 🟡 override/suspend any |
| Approve/reject parking listing | ⛔ | ⛔ | ⛔ | ✅ |
| Configure sections/pricing/capacity | ⛔ | 🟡 own listings only | ⛔ | 🟡 override |
| Toggle Instant Mode per category | ⛔ | 🟡 own listings only | ⛔ | 🟡 override/kill-switch |
| Create booking (advance/instant) | ✅ | ⛔ | ⛔ | 🟡 admin-assisted booking (support flow) |
| Cancel own booking | ✅ | ⛔ | ⛔ | ✅ (ADMIN_CANCELLED) |
| View/manage bookings for own parking | ⛔ | 🟡 own listings only | 🟡 assigned parking, read + check-in/out actions only | ✅ |
| Scan QR / check-in / check-out | ⛔ | 🟡 own listings only (owner-operated small lots) | 🟡 assigned parking only | ✅ (support/investigation) |
| Report incident | ✅ (as reporter) | 🟡 own listings | 🟡 assigned parking | ✅ |
| Initiate payment | ✅ | ⛔ | ⛔ | ⛔ |
| View own payment history / receipts | ✅ | ⛔ | ⛔ | 🟡 support access |
| View own earnings/settlement | ⛔ | 🟡 own listings only | ⛔ | ✅ all owners |
| Request settlement | ⛔ | 🟡 own, subject to verification | ⛔ | 🟡 approve/override |
| Configure commission/tax/fees | ⛔ | ⛔ | ⛔ | ✅ |
| Issue refunds | ⛔ | ⛔ (may *request* via dispute) | ⛔ | ✅ |
| View/verify owner KYC | ⛔ | 🟡 own status only | ⛔ | ✅ |
| Manage users/roles | ⛔ | ⛔ | ⛔ | ✅ |
| View fraud alerts / anomaly scores | ⛔ | ⛔ | ⛔ | ✅ |
| Ban/suspend account (final action) | ⛔ | ⛔ | ⛔ | ✅ (always via review workflow, never automatic) |
| Support ticket: create | ✅ | ✅ | ✅ | 🟡 on behalf of user |
| Support ticket: resolve/close | ⛔ | ⛔ | ⛔ | ✅ |
| Dispute: file | ✅ | ✅ | 🟡 file incident, not financial dispute | 🟡 on behalf of user |
| Dispute: adjudicate | ⛔ | ⛔ | ⛔ | ✅ |
| View audit logs | ⛔ | 🟡 own resource audit trail (e.g., own listing history) | ⛔ | ✅ |
| Emergency closure (listing/section/category) | ⛔ | 🟡 own listing, non-emergency pause only | ⛔ | ✅ (true emergency override) |
| Configure feature flags | ⛔ | ⛔ | ⛔ | ✅ |
| Reconciliation view | ⛔ | ⛔ | ⛔ | ✅ |

**Enforcement pattern (every protected endpoint):** `JwtAuthGuard` (is this a valid, non-expired session?) → `RolesGuard` (`@Roles('OWNER')` etc.) → `ResourceOwnershipGuard` (does `req.user.id` own/administer the specific `:parkingId`/`:bookingId`/`:vehicleId` in the path, and is the resource in a status that permits this action?) → service-layer business rule checks (e.g., attendant's `assigned_parking_ids` includes this parking; booking's vehicle category is within attendant's authorized categories if that restriction is configured). All four layers are unit- and integration-tested per endpoint; the Android app additionally hides inaccessible UI, but that is UX polish, never the security boundary.

---

## 5. Database Entity-Relationship Design

Grouped by domain. All primary keys are UUIDv7 (time-sortable, avoids sequential-ID enumeration attacks). All monetary columns are `BIGINT` minor units (paise) with a separate `currency CHAR(3)` column. All tables have `created_at`, `updated_at`; mutable business entities also have soft-delete/status columns rather than hard deletes where history must be preserved.

### 5.1 Identity & roles
- **users**: id, phone (unique, nullable if email-only), email (unique, nullable), phone_verified_at, email_verified_at, password_hash (nullable if OAuth-only), google_subject_id (nullable, unique), profile_photo_url, status (`ACTIVE`/`SUSPENDED`/`DELETED`), created_at.
- **user_roles**: id, user_id → users, role (`DRIVER`/`OWNER`/`ATTENDANT`/`ADMIN`), granted_by, granted_at, status.
- **sessions**: id, user_id, refresh_token_hash, device_id, device_info, ip_address, user_agent, issued_at, expires_at, revoked_at.
- **otp_challenges**: id, phone_or_email, purpose (`LOGIN`/`REGISTER`/`RESET_PASSWORD`), code_hash, attempt_count, expires_at, consumed_at, request_ip.
- **admin_permissions**: id, user_id, permission_key (fine-grained, e.g. `FINANCE_REFUND_APPROVE`), granted_by, granted_at.

### 5.2 Drivers & vehicles
- **driver_profiles**: id, user_id → users, default_vehicle_id (nullable FK), licence_number (encrypted at rest), licence_verified_at.
- **vehicles**: id, driver_id → users, category (`TWO_WHEELER`/`FOUR_WHEELER`/`OTHER_SUPPORTED`/`UNSUPPORTED_PENDING_REVIEW`), vehicle_type (`BIKE`/`SCOOTER`/`CAR`/`SUV`/`EV`/`OTHER`), size (`SMALL`/`MEDIUM`/`LARGE`/`EXTRA_LARGE`, nullable), registration_number (normalized + validated per Indian format, unique per driver), make, model (nullable), is_default (bool), status (`ACTIVE`/`REMOVED`), created_at.
  - Index: `(driver_id, is_default)`, unique `(registration_number)` scoped per active vehicle to catch duplicates.

### 5.3 Owners & KYC
- **owner_profiles**: id, user_id → users, business_name (nullable), parking_type_default, verification_status (`NOT_STARTED`/`SUBMITTED`/`UNDER_REVIEW`/`APPROVED`/`REJECTED`/`NEEDS_MORE_INFORMATION`/`SUSPENDED`), verified_at, verified_by (admin user id).
- **owner_kyc_documents**: id, owner_id, document_type, storage_key (private bucket), status, reviewed_by, reviewed_at.
- **owner_payout_accounts**: id, owner_id, method (`BANK`/`UPI`), account_holder_name, bank_account_number_encrypted, ifsc, upi_vpa_encrypted, verification_status, is_primary, created_at.

### 5.4 Attendants
- **attendant_profiles**: id, user_id → users, employer_owner_id (nullable, if owner-hired) or `ADMIN`-managed, status.
- **attendant_assignments**: id, attendant_id, parking_id → parking_listings, authorized_categories (`[TWO_WHEELER, FOUR_WHEELER]` array or both), assigned_by, assigned_at, revoked_at (nullable).

### 5.5 Parking & sections
- **parking_listings**: id, owner_id, name, parking_type (`INDIVIDUAL`/`RESIDENTIAL`/`APARTMENT`/`COMMERCIAL`/`OFFICE`/`MALL`/`OTHER`), description, approval_status (`PENDING`/`APPROVED`/`REJECTED`/`NEEDS_MORE_INFORMATION`/`SUSPENDED`), status (`ACTIVE`/`PAUSED`/`CLOSED`), timezone, created_at.
- **parking_locations**: id, parking_id (1:1), latitude, longitude, `geog geography(Point,4326)` (generated/synced, GiST-indexed), address_line, city, state, postal_code, entrance_notes, location_accuracy_meters, last_updated_by, last_updated_at.
- **parking_photos**: id, parking_id, section_id (nullable = listing-level photo), photo_type (`LISTING`/`ENTRANCE`/`SECTION`), storage_key, uploaded_by, uploaded_at, status (`ACTIVE`/`REMOVED`).
- **parking_sections**: id, parking_id, name, vehicle_category (`TWO_WHEELER`/`FOUR_WHEELER`), supported_vehicle_types (array), capacity, is_covered (bool), has_security (bool), has_cctv (bool), has_ev_charging (bool), instant_mode_enabled (bool), operating_hours (jsonb, per-weekday open/close), status (`ACTIVE`/`PAUSED`/`CLOSED`), approval_status, location_notes, created_at.
  - Index: `(parking_id, vehicle_category)`.
- **parking_section_rules**: id, section_id, rule_type (`MAX_VEHICLE_SIZE`/`HELMET_STORAGE`/`HEIGHT_RESTRICTION`/`DURATION_LIMIT`/`COMMERCIAL_VEHICLE_RESTRICTED`/…), rule_value (jsonb).
- **parking_spaces** *(optional space-level granularity)*: id, section_id, space_label, size (`SMALL`/`MEDIUM`/`LARGE`/`EXTRA_LARGE`, nullable), length_cm, width_cm, height_clearance_cm, weight_limit_kg, is_ev_capable, is_covered, is_accessible, status (`AVAILABLE`/`RESERVED`/`OCCUPIED`/`BLOCKED`/`MAINTENANCE`/`INACTIVE`), active (bool).
- **convertible_space_rules** *(explicit, admin-approved only)*: id, section_id_a, section_id_b, approved_by, approved_at, active (bool) — existence of a row is the only way capacity can ever move between categories, and every conversion event is separately audit-logged.

### 5.6 Availability & occupancy
- **section_availability**: id, section_id (1:1 today; extendable to per-date rows for advance-booking calendars), capacity, reserved_count, occupied_count, blocked_count, available_count (generated: `capacity - reserved_count - occupied_count - blocked_count`), version (optimistic-lock counter), updated_at.
  - `CHECK (reserved_count + occupied_count + blocked_count <= capacity)`, `CHECK (available_count >= 0)`.
- **booking_holds**: id, section_id, vehicle_category, driver_id, quantity (=1 normally), expires_at, released_at (nullable), booking_id (nullable until confirmed).
  - Purpose: short-lived (e.g., 10 min) reservation created at "Confirm Booking Details" step, decremented from `available_count` via `reserved_count`, auto-expired by a BullMQ job if payment isn't completed.

### 5.7 Bookings
- **bookings**: id, driver_id, vehicle_id, parking_id, section_id, vehicle_category (denormalized at booking time), booking_type (`ADVANCE`/`INSTANT`), status (see §8), start_time, end_time (nullable for instant/open-ended), actual_check_in_at, actual_check_out_at, price_snapshot (jsonb — the full computed breakdown, frozen), policy_snapshot (jsonb — cancellation/commission/tax config id references, frozen), qr_pass_id, created_at.
- **booking_events**: id, booking_id, actor_id (nullable for system), actor_role, source (`APP`/`ATTENDANT_APP`/`ADMIN`/`SYSTEM_JOB`/`WEBHOOK`), old_status, new_status, reason, metadata (jsonb), created_at. Append-only.
- **vehicle_mismatch_events**: id, booking_id, reported_by (attendant id), original_category, actual_category, original_vehicle_id, actual_vehicle_registration, resolution (`REJECTED_ENTRY`/`RECATEGORIZED_WITH_PAYMENT_DIFF`/`ADMIN_OVERRIDE`), price_diff_amount (nullable), created_at.

### 5.8 Payments & ledger
- **payment_orders**: id, booking_id, driver_id, gateway (`RAZORPAY`/…), gateway_order_id, amount, currency, status (`PENDING`/`PROCESSING`/`SUCCESSFUL`/`FAILED`), idempotency_key (unique), created_at.
- **payment_gateway_events**: id, payment_order_id (nullable until matched), gateway, event_type, gateway_event_id (unique per gateway to dedupe), raw_payload (jsonb, secrets redacted), signature_verified (bool), processed_at, correlation_id.
- **transactions** *(the ledger)*: id, booking_id, driver_id, owner_id, parking_id, section_id, vehicle_id, vehicle_category, vehicle_type, parking_amount, platform_fee, commission_amount, tax_amount, driver_total, owner_gross, owner_net, payment_method (`ONLINE`/`CASH`), gateway, gateway_transaction_id (nullable for cash), payment_status, booking_status_at_time, refund_status, settlement_status, currency, idempotency_key, created_at. Immutable after creation; corrections are new **transaction_adjustments** rows.
- **transaction_adjustments**: id, transaction_id, adjustment_type (`REFUND`/`CORRECTION`/`CHARGEBACK`/`FEE_REVERSAL`), amount, reason, created_by, created_at.
- **cash_collections**: id, booking_id, collected_by (owner or attendant user id), amount, confirmed_at, confirmation_method, audit_note.
- **refunds**: id, transaction_id, booking_id, refund_type (`FULL`/`PARTIAL`/`NONE`), reason_code (`CATEGORY_MISMATCH`/`PARKING_UNAVAILABLE`/`ADMIN_APPROVED`/`CANCELLATION_POLICY`/…), amount, status (`PENDING`/`PROCESSING`/`COMPLETED`/`FAILED`), gateway_refund_id (nullable for cash), initiated_by, approved_by (nullable), created_at, completed_at.

### 5.9 Commission/tax configuration (versioned)
- **commission_policies**: id, scope (`GLOBAL`/`PARKING`/`SECTION`/`VEHICLE_CATEGORY`/`OWNER`/`PROMOTION`), scope_ref_id (nullable), commission_percent, fixed_fee, min_commission, effective_from, effective_to (nullable), created_by.
- **tax_policies**: id, scope, scope_ref_id (nullable), tax_type (`GST`/…), rate_percent, inclusive (bool), effective_from, effective_to.
- **cancellation_policies**: id, scope, scope_ref_id, rules (jsonb: time-based refund tiers), effective_from, effective_to.
  - Every `transactions` row references the *policy version ids* used, not just the resulting numbers, so an audit can reconstruct exactly which rule produced a given charge.

### 5.10 Owner earnings & settlements
- **owner_earnings_ledger**: id, owner_id, parking_id, section_id, vehicle_category, booking_id, transaction_id, amount, status (`PENDING`/`AVAILABLE`/`PROCESSING`/`SETTLED`/`FAILED`/`ADJUSTED`/`REVERSED`), created_at.
- **settlements**: id, owner_id, requested_amount, covered_earning_ids (array or join table `settlement_items`), status (`PENDING`/`PROCESSING`/`SETTLED`/`FAILED`/`REVERSED`), payout_account_id, gateway_payout_id (nullable), requested_at, processed_at.
- **settlement_items**: id, settlement_id, owner_earnings_ledger_id (unique — prevents an earning being settled twice).

### 5.11 QR & check-in
- **qr_passes**: id, booking_id (unique), payload_hash (the QR encodes a signed token referencing this id, not raw booking data), issued_at, expires_at, used_at (nullable), status (`ACTIVE`/`USED`/`EXPIRED`/`REVOKED`).
- **check_events**: id, booking_id, qr_pass_id, event_type (`CHECK_IN`/`CHECK_OUT`), attendant_id (nullable if owner self-service), parking_id, section_id, verification_outcome (`OK`/`CATEGORY_MISMATCH`/`VEHICLE_NUMBER_MISMATCH`/`EXPIRED`/`ALREADY_USED`), device_metadata (jsonb), created_at. Unique constraint prevents two `CHECK_IN` events for one booking without an intervening `CHECK_OUT`.

### 5.12 Reviews
- **reviews**: id, booking_id (unique — one review per completed booking), driver_id, parking_id, ratings (jsonb: cleanliness/security/location/overall), comment, created_at, status (`ACTIVE`/`FLAGGED`/`REMOVED`).
- **owner_driver_ratings**: id, booking_id (unique), owner_id, driver_id, rating, comment, created_at.

### 5.13 Notifications
- **notification_devices**: id, user_id, fcm_token, platform, last_seen_at, revoked_at.
- **notifications**: id, user_id, type, title, body, deep_link, data (jsonb, minimized), read_at, created_at.
- **notification_preferences**: id, user_id, category, channel (`PUSH`/`SMS`/`EMAIL`), enabled (bool).

### 5.14 Fraud
- **fraud_signals**: id, subject_type (`USER`/`LISTING`/`REVIEW`/`BOOKING`), subject_id, signal_type, score, explanation (jsonb — must be human-readable, not a black box), computed_at.
- **fraud_alerts**: id, fraud_signal_id, status (`OPEN`/`UNDER_REVIEW`/`DISMISSED`/`ACTION_TAKEN`), reviewed_by, reviewed_at, action_taken (nullable).
- **photo_hashes**: id, parking_photo_id, perceptual_hash, matched_photo_ids (array), created_at.

### 5.15 Support & disputes
- **support_tickets**: id, user_id, category, subject, description, status (`OPEN`/`IN_PROGRESS`/`WAITING_FOR_USER`/`RESOLVED`/`CLOSED`/`REOPENED`), assigned_to (admin id), related_booking_id (nullable), created_at.
- **support_messages**: id, ticket_id, sender_id, sender_role, message, attachments (array of storage keys), created_at.
- **disputes**: id, booking_id, filed_by, dispute_type, status, resolution, resolved_by, created_at, resolved_at.
- **dispute_evidence**: id, dispute_id, evidence_type (`PHOTO`/`CHECK_EVENT`/`QR_SCAN`/`PAYMENT_REF`/`LOCATION_RECORD`/`MESSAGE`), storage_key (nullable), reference_id (nullable, e.g. check_events.id), added_by, added_at, visibility (`ADMIN_ONLY`/`PARTIES`).

### 5.16 Platform/admin
- **service_areas**: id, name, boundary (geography polygon), status, created_by.
- **feature_flags**: id, key, description, enabled_globally, rollout_rules (jsonb: role/service-area/percentage).
- **audit_logs**: id, actor_id, actor_role, action, target_type, target_id, before_state (jsonb, nullable), after_state (jsonb, nullable), ip_address, correlation_id, created_at. Append-only, no update/delete API.
- **config_settings**: id, key, value (jsonb), environment, updated_by, updated_at.

### 5.17 Key indexes (beyond PKs/FKs)
- `parking_locations.geog` — GiST index for radius search.
- `section_availability(section_id)` unique, row-locked on write.
- `bookings(driver_id, status)`, `bookings(section_id, status, start_time)`, `bookings(status, created_at)` for the hold-expiry sweep.
- `transactions(owner_id, created_at)`, `transactions(booking_id)`, `transactions(gateway_transaction_id)`.
- `owner_earnings_ledger(owner_id, status)` for settlement eligibility queries.
- `vehicles(registration_number)` for lookup during check-in.
- `payment_gateway_events(gateway, gateway_event_id)` unique — webhook dedupe.
- `audit_logs(target_type, target_id, created_at)`, `fraud_alerts(status)`, `support_tickets(status, assigned_to)`.

---

## 6. Vehicle & Parking Segregation Model

This is the feature the rest of the system is built around, so it gets its own section rather than being buried in the ERD.

**Category enum (fixed, backend-owned):** `TWO_WHEELER`, `FOUR_WHEELER`, `OTHER_SUPPORTED`, `UNSUPPORTED_PENDING_REVIEW`. Vehicle *type* (`BIKE`/`SCOOTER`/`CAR`/`SUV`/`EV`/`OTHER`) is a sub-classification within a category, never a substitute for it — search, pricing, and capacity logic always key off `category`, and `vehicle_type`/`size` are used only for finer compatibility checks (e.g., an `EXTRA_LARGE` SUV against a section's `max_vehicle_size` rule).

**Hierarchy:** `parking_listings` (1) → `parking_sections` (many, each with exactly one `vehicle_category`) → optionally `parking_spaces` (many, inheriting the section's category). A listing with both two-wheeler and four-wheeler parking has **two section rows**, never one row with a combined counter. This is enforced at the schema level (`parking_sections.vehicle_category NOT NULL`, no "mixed" value exists) so it is structurally impossible to build a screen that shows one blended availability number.

**Capacity isolation:** `section_availability` is keyed 1:1 to `section_id`, and since a section has exactly one category, category isolation falls directly out of the schema — a four-wheeler booking can only ever touch a four-wheeler section's row. The only sanctioned way capacity moves between categories is a `convertible_space_rules` row created by an Admin action (never automatic, always audited).

**Compatibility check (runs server-side on every search, hold, and booking-confirm call):**
```
is_bookable(vehicle, section) :=
    vehicle.category == section.vehicle_category
    AND vehicle.vehicle_type IN section.supported_vehicle_types
    AND (section has no size rule OR vehicle.size satisfies section's max_vehicle_size rule)
    AND section.status == ACTIVE AND section.approval_status == APPROVED
    AND section.instant_mode_enabled == true   (only for the Instant Parking path)
    AND section_availability.available_count > 0
```
A request that fails this check never reaches the booking-hold step; the API returns a typed reason (`CATEGORY_MISMATCH`, `SIZE_INCOMPATIBLE`, `SECTION_INACTIVE`, `NO_AVAILABILITY`) so the Android client can show *why*, per the spec's "explain incompatibility" requirement.

**Search behavior:** the search endpoint requires a `vehicleId` (or explicit `category` for anonymous/pre-vehicle browsing) and by default returns only sections passing the compatibility check; a filter lets the driver widen to "All compatible vehicles" but never surfaces a section the selected vehicle cannot use as "bookable." Per-listing search results carry **per-category availability counts** (e.g., `two_wheeler: {available: 12}`, `four_wheeler: {available: 0}`), never a single blended "Available" badge.

**QR/check-in validation** re-runs the same `is_bookable` predicate against the vehicle actually presented (registration number entered/scanned by the attendant), not just the vehicle on file at booking time — this is what powers the mismatch workflow in §5.7/§9.

**Historical integrity:** `bookings.vehicle_category`, `bookings.price_snapshot`, and `bookings.policy_snapshot` freeze the category and every number/rule used, so a later change to a section's category composition, pricing, or commission config never rewrites past bookings' figures.

---

## 7. API Endpoint Inventory

Full per-endpoint documentation (request/response schemas, error codes, rate limits) will ship as `openapi.yaml` starting Milestone 1. Below is the inventory by module with method, path, and auth/role — the contract Milestone 1's scaffolding targets.

### 7.1 Auth
| Method | Path | Auth |
|---|---|---|
| POST | /auth/otp/request | Public, rate-limited |
| POST | /auth/otp/verify | Public, rate-limited |
| POST | /auth/register | Public |
| POST | /auth/login | Public, rate-limited + lockout |
| POST | /auth/google | Public |
| POST | /auth/refresh | Refresh token |
| POST | /auth/forgot-password | Public, rate-limited |
| POST | /auth/reset-password | Token from forgot-password flow |
| POST | /auth/change-password | Authenticated |
| POST | /auth/logout | Authenticated |
| GET | /auth/sessions | Authenticated (own sessions) |
| DELETE | /auth/sessions/:id | Authenticated (own session) |
| DELETE | /auth/account | Authenticated + re-auth |

### 7.2 Users / Vehicles
| Method | Path | Auth |
|---|---|---|
| GET/PATCH | /users/me | Authenticated |
| POST | /users/me/roles/owner | Authenticated (self-upgrade to Owner, triggers onboarding) |
| GET | /vehicles | DRIVER (own) |
| POST | /vehicles | DRIVER |
| PATCH/DELETE | /vehicles/:id | DRIVER (own) |
| POST | /vehicles/:id/default | DRIVER (own) |

### 7.3 Parking / Sections / Spaces / Location
| Method | Path | Auth |
|---|---|---|
| POST | /owner/parking | OWNER |
| GET/PATCH | /owner/parking/:id | OWNER (own) |
| POST | /owner/parking/:id/sections | OWNER (own) |
| PATCH | /owner/parking/:id/sections/:sectionId | OWNER (own) |
| POST | /owner/parking/:id/sections/:sectionId/spaces | OWNER (own) |
| POST | /owner/parking/:id/photos | OWNER (own), MIME/size validated |
| PUT | /owner/parking/:id/location | OWNER (own), audit-logged |
| POST | /owner/parking/:id/submit-for-approval | OWNER (own) |
| GET | /admin/parking/pending | ADMIN |
| POST | /admin/parking/:id/approve \| reject | ADMIN |
| POST | /admin/parking/:id/sections/:sectionId/approve \| reject | ADMIN |

### 7.4 Discovery / Search / Favorites
| Method | Path | Auth |
|---|---|---|
| GET | /search/parking?lat&lng&radius&vehicleId&category&filters&sort&page | Authenticated (DRIVER) |
| GET | /parking/:id | Authenticated |
| GET | /parking/:id/sections | Authenticated |
| POST/DELETE | /favorites/:parkingId | DRIVER |
| GET | /favorites | DRIVER (own) |

### 7.5 Availability
| Method | Path | Auth |
|---|---|---|
| GET | /parking/:id/availability?category | Authenticated |
| PATCH | /owner/parking/:id/sections/:sectionId/availability | OWNER (own) |
| POST | /owner/parking/:id/sections/:sectionId/pause \| resume | OWNER (own) |
| POST | /owner/parking/:id/sections/:sectionId/instant-mode | OWNER (own) |

### 7.6 Booking
| Method | Path | Auth |
|---|---|---|
| POST | /bookings/holds | DRIVER, idempotent |
| POST | /bookings | DRIVER, idempotent (confirms a hold → PENDING_PAYMENT) |
| POST | /bookings/instant | DRIVER, idempotent |
| GET | /bookings | DRIVER (own) / OWNER (own parking) / ATTENDANT (assigned) / ADMIN |
| GET | /bookings/:id | Role+ownership scoped |
| POST | /bookings/:id/cancel | DRIVER (own) / ADMIN |
| POST | /admin/bookings/:id/cancel | ADMIN |

### 7.7 Payments
| Method | Path | Auth |
|---|---|---|
| POST | /payments/orders | DRIVER, idempotent |
| POST | /payments/webhooks/:gateway | Public (signature-verified), idempotent |
| GET | /payments/:id | DRIVER (own) / ADMIN |
| POST | /payments/:id/retry | DRIVER (own) |
| POST | /bookings/:id/cash-collect | OWNER/ATTENDANT (assigned) |

### 7.8 Refunds / Ledger / Settlements
| Method | Path | Auth |
|---|---|---|
| POST | /refunds | ADMIN (or automated policy engine acting as SYSTEM, always audit-logged) |
| GET | /refunds/:id | Scoped |
| GET | /owner/earnings | OWNER (own) |
| POST | /owner/settlements | OWNER (own, verified account required) |
| GET | /owner/settlements | OWNER (own) |
| GET | /admin/settlements | ADMIN |
| POST | /admin/settlements/:id/process \| reverse | ADMIN |
| GET | /admin/transactions | ADMIN, filterable per §14 |
| GET | /admin/reconciliation/exceptions | ADMIN |

### 7.9 QR / Check-in
| Method | Path | Auth |
|---|---|---|
| GET | /bookings/:id/pass | DRIVER (own) |
| POST | /attendant/qr/scan | ATTENDANT (assigned parking only) / OWNER (own) |
| POST | /attendant/bookings/:id/check-in | ATTENDANT/OWNER, scoped |
| POST | /attendant/bookings/:id/check-out | ATTENDANT/OWNER, scoped |
| POST | /attendant/bookings/:id/mismatch | ATTENDANT/OWNER, scoped, audit-logged |

### 7.10 Notifications / Reviews / Support / Disputes / Fraud / Admin / Reports / Audit
| Method | Path | Auth |
|---|---|---|
| POST | /notifications/devices | Authenticated |
| GET/PATCH | /notifications/preferences | Authenticated |
| GET | /notifications | Authenticated (own) |
| POST | /reviews | DRIVER (booking must be COMPLETED, one per booking) |
| POST | /support/tickets | Authenticated |
| GET | /support/tickets | Scoped |
| POST | /disputes | Authenticated |
| GET | /admin/fraud/alerts | ADMIN |
| POST | /admin/fraud/alerts/:id/review | ADMIN |
| GET | /admin/users, /admin/owners, /admin/audit-logs | ADMIN |
| GET | /admin/reports/* , /owner/analytics | Role-scoped |
| GET/PATCH | /admin/config/commission, /admin/config/tax, /admin/config/feature-flags | ADMIN |

Every list endpoint supports pagination (`cursor`/`limit`), and every mutating endpoint that isn't naturally idempotent (POST creating a resource) accepts an `Idempotency-Key` header honored via a Redis-backed dedupe table, per the spec's idempotency requirement.

---

## 8. Booking State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT: hold confirmed
    PENDING_PAYMENT --> CONFIRMED: payment SUCCESSFUL (or cash policy allows)
    PENDING_PAYMENT --> EXPIRED: hold TTL elapsed, unpaid
    PENDING_PAYMENT --> CANCELLED: driver cancels before payment
    CONFIRMED --> DRIVER_ARRIVING: driver marks en route (optional, instant flow)
    CONFIRMED --> CHECKED_IN: attendant/owner check-in scan
    DRIVER_ARRIVING --> CHECKED_IN: attendant/owner check-in scan
    CONFIRMED --> CANCELLED: driver cancels per policy
    CONFIRMED --> NO_SHOW: window elapsed, no check-in
    CONFIRMED --> ADMIN_CANCELLED: admin action
    CONFIRMED --> PARKING_UNAVAILABLE: owner reports section unavailable
    CHECKED_IN --> PARKING_ACTIVE: system marks active on successful check-in
    PARKING_ACTIVE --> VEHICLE_MISMATCH: attendant reports category/vehicle mismatch
    VEHICLE_MISMATCH --> PARKING_ACTIVE: authorized resolution (re-categorized + paid diff)
    VEHICLE_MISMATCH --> REJECTED: entry rejected, no valid resolution
    PARKING_ACTIVE --> CHECKED_OUT: attendant/owner check-out scan
    CHECKED_OUT --> COMPLETED: system finalizes ledger/earnings
    REJECTED --> [*]
    EXPIRED --> [*]
    CANCELLED --> [*]
    NO_SHOW --> [*]
    ADMIN_CANCELLED --> [*]
    PARKING_UNAVAILABLE --> [*]
    COMPLETED --> [*]
```

Rules: every transition is written via a single `applyBookingTransition(bookingId, expectedCurrentStatus, newStatus, actor, reason)` service method that (a) checks the transition is in an explicit allow-list, (b) uses the row's optimistic-lock `version`/`expectedCurrentStatus` to make retries idempotent (a duplicate "check-in" call that arrives twice is a no-op the second time, not an error masking a real problem), and (c) writes a `booking_events` row in the same transaction. Terminal states (`COMPLETED`, `REJECTED`, `EXPIRED`, `CANCELLED`, `NO_SHOW`, `ADMIN_CANCELLED`, `PARKING_UNAVAILABLE`) never transition further; capacity release (decrementing `reserved_count`/`occupied_count`) happens atomically alongside every transition that leaves a section, so availability can never drift from booking reality.

---

## 9. Payment State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: order created
    PENDING --> PROCESSING: gateway redirect/SDK invoked
    PROCESSING --> SUCCESSFUL: webhook + backend verification match
    PROCESSING --> FAILED: gateway failure/timeout/webhook reports failure
    FAILED --> PENDING: driver retries (new order, same booking hold if still valid)
    SUCCESSFUL --> REFUNDED: full refund completed
    SUCCESSFUL --> PARTIALLY_REFUNDED: partial refund completed
    PARTIALLY_REFUNDED --> REFUNDED: subsequent full refund of remainder
    FAILED --> [*]
    SUCCESSFUL --> [*]
    REFUNDED --> [*]
    PARTIALLY_REFUNDED --> [*]
```

`SUCCESSFUL` is only reachable through the webhook-verification path (§Payment Webhooks in the spec): the gateway callback the app receives client-side moves the UI to an optimistic "verifying" state, but the `payment_orders.status` row only flips to `SUCCESSFUL` after the backend independently verifies the signed webhook (or a synchronous verify-payment call to the gateway API) matches booking id, amount, and currency. A booking only becomes `CONFIRMED` off the back of `SUCCESSFUL`, never off the client's callback alone.

---

## 10. Refund State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: refund requested/policy-triggered
    PENDING --> PROCESSING: submitted to gateway (online) or manual workflow started (cash)
    PROCESSING --> COMPLETED: gateway confirms / manual confirmation recorded
    PROCESSING --> FAILED: gateway rejects/errors
    FAILED --> PENDING: safe retry (idempotency key reused, no double refund)
    COMPLETED --> [*]
    FAILED --> [*]
```

Refund `amount` and `reason_code` are always computed server-side from the `cancellation_policies` version frozen on the booking plus the trigger (`CANCELLATION_POLICY`/`CATEGORY_MISMATCH`/`PARKING_UNAVAILABLE`/`ADMIN_APPROVED`). A `COMPLETED` refund creates a `transaction_adjustments` row and updates `owner_earnings_ledger` (reducing `AVAILABLE`/`PENDING` earnings or creating a `REVERSED` entry if already settled) — the original `transactions` row is never edited in place.

---

## 11. Settlement State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING: settlement requested against eligible balance
    PENDING --> PROCESSING: validation passed (verified payout account, no double-claim)
    PROCESSING --> SETTLED: payout provider confirms
    PROCESSING --> FAILED: payout provider rejects/errors
    FAILED --> PENDING: owner/admin re-initiates (new idempotency key)
    SETTLED --> REVERSED: clawback (e.g., post-settlement chargeback discovered)
    SETTLED --> [*]
    REVERSED --> [*]
```

`settlement_items` gives each `owner_earnings_ledger` row a unique settlement reference, which is the mechanism that makes "prevent duplicate payouts" structural rather than procedural: an earning already linked to a non-`FAILED` settlement cannot be selected into a new settlement request (enforced by a unique constraint plus a `SELECT … FOR UPDATE` on the candidate earnings during settlement creation).

---

## 12. Availability & Occupancy Model

**Unit of concurrency control:** one row per `parking_sections.id` in `section_availability`. Every capacity-affecting operation (create hold, expire hold, confirm booking, cancel booking, check-in, check-out, owner manual capacity edit, block-for-maintenance) runs as:

```sql
BEGIN;
SELECT * FROM section_availability WHERE section_id = $1 FOR UPDATE;
-- validate invariant (available_count would not go negative, capacity not exceeded)
UPDATE section_availability SET reserved_count = ..., version = version + 1 WHERE section_id = $1;
INSERT INTO booking_events / booking_holds / audit_logs ...;
COMMIT;
```

This serializes concurrent writers on the same section without locking unrelated sections, so a surge of bookings on Section A never slows down Section B. For very high-traffic sections, the same invariant can be enforced via a Redis `DECR`-with-floor Lua script guarding a fast path, with Postgres as the durable source of truth reconciled asynchronously — deferred to a later milestone only if load testing (Milestone 12) shows Postgres row-locking is the bottleneck; Postgres alone is the Milestone 1–9 implementation.

**Hold lifecycle:** `POST /bookings/holds` creates a `booking_holds` row and increments `reserved_count`, with `expires_at = now() + HOLD_TTL_SECONDS` (default 10 minutes, configurable). A BullMQ delayed job scheduled at hold creation calls a `releaseHoldIfUnpaid` job at `expires_at`; if the hold hasn't been converted to a paid booking, it decrements `reserved_count` back down and marks the hold `released_at`. The same job is idempotent (checks the hold isn't already released/converted before acting), and a periodic sweep job independently catches any holds the delayed job missed (e.g., after a deploy restart) — satisfying "safe after server restarts."

**Category isolation in this model** falls out of §6: since `section_availability` is 1:1 with a single-category section, a two-wheeler hold/booking only ever locks and mutates a two-wheeler section's row.

**Occupancy vs. reservation vs. block:** `reserved_count` = paid/confirmed bookings not yet checked in; `occupied_count` = currently checked-in vehicles; `blocked_count` = owner-blocked (maintenance) spaces. Check-in moves a unit from `reserved` to `occupied`; check-out moves it out of `occupied` entirely (space freed). `available_count` is a generated column so it can never be independently corrupted by a partial update.

**Space-level mode:** when an owner enables space-level tracking, `parking_spaces.status` is the fine-grained view, and `section_availability` counts are kept in sync via the same transaction that changes a space's status (a trigger or application-level dual-write within one transaction — decided at Milestone 6 based on operational complexity; both keep the section row as the authoritative capacity source for booking decisions).

---

## 13. Threat Model

STRIDE-style pass over the highest-risk flows. Full threat model (with mitigations mapped to test cases) will be maintained as a living document starting Milestone 1; this is the Milestone 0 summary.

| # | Asset / Flow | Threat | Mitigation |
|---|---|---|---|
| T1 | Auth/session | Credential stuffing, brute force on login/OTP | Rate limiting + progressive lockout on `/auth/login` and `/auth/otp/*`; OTP attempt caps; generic error messages that don't reveal whether phone/email exists. |
| T2 | JWT/session tokens | Token theft → account takeover | Short-lived access tokens, rotated refresh tokens (reuse detection revokes the whole session family), device/session list + remote revoke, HTTPS-only, no tokens in logs. |
| T3 | Role/ownership APIs | Broken object-level authorization (BOLA) — e.g., owner A fetching owner B's booking by guessing an ID | `ResourceOwnershipGuard` on every scoped endpoint, checked against `req.user`, never against a client-supplied "ownerId"; IDs are UUIDv7 (non-enumerable) as defense in depth, not as the primary control. |
| T4 | Attendant scope | Attendant scans/checks-in bookings outside their assigned parking | `attendant_assignments` checked server-side on every scan/check-in/check-out call; UI hiding is not relied upon. |
| T5 | Booking/availability | Race condition double-books the last space | Row-level locking per §12; integration tests specifically fire concurrent requests at the same section to assert only one wins. |
| T6 | Pricing | Client sends a manipulated price/amount | Backend always recomputes price from `commission_policies`/`tax_policies`/section pricing server-side; client-sent price fields are ignored, not merely "validated." |
| T7 | Payment webhooks | Forged webhook fabricates a successful payment | Signature verification against gateway secret (never accept unsigned/mismatched-signature payloads); amount/currency/booking cross-check against the `payment_orders` row before marking `SUCCESSFUL`. |
| T8 | Payment webhooks | Replay of a captured legitimate webhook | `payment_gateway_events(gateway, gateway_event_id)` unique constraint — second delivery is a no-op, logged not reprocessed. |
| T9 | QR pass | QR photographed and reused after checkout, or shared with a second vehicle | QR encodes a signed reference to `qr_passes.id`; `status` flips to `USED` atomically on check-in with a unique partial index preventing two `CHECK_IN` events; re-scan after use returns `ALREADY_USED` and creates an audit event rather than a silent success. |
| T10 | Settlement | Duplicate or over-limit payout, or payout to unverified account | `settlement_items` uniqueness per earning + `FOR UPDATE` on candidate earnings + payout-account `verification_status == VERIFIED` gate before any settlement request is accepted. |
| T11 | File uploads (photos/KYC) | Malicious file disguised as image, path traversal, oversized payloads | Strict MIME allow-list + magic-byte sniffing (not just extension), size caps, server-generated storage keys (never client-supplied paths), private bucket + signed URLs for KYC, malware-scan integration boundary (hook point defined even if the actual scanner is added later per available budget/vendor). |
| T12 | KYC/bank details | Sensitive data exposure via logs, API responses, or DB dump | Field-level encryption for account numbers/licence numbers, log redaction middleware (deny-list of field names), KYC docs never returned as public URLs. |
| T13 | Fraud/anomaly | Automated action bans a legitimate user on a false-positive score | Anomaly scores only ever create a `fraud_alerts` row for human review; no code path auto-suspends an account from a score alone. |
| T14 | Admin actions | A compromised or careless admin account causes large-scale damage (mass refunds, mass suspensions) | Fine-grained `admin_permissions` (not a single blanket ADMIN bit), sensitive actions (refund above threshold, bulk suspension) require a second factor / dual control, every admin mutation is `audit_logs`-recorded with before/after state. |
| T15 | Location data | Owner's exact address/entrance exposed to non-relevant parties, or a driver's live location over-collected | Only approved-listing entrance coordinates are public; owner personal contact info is never in the public listing payload; driver location is used transiently for search/instant-parking and not persisted beyond what's needed for the active session/booking. |
| T16 | Reviews | Fake/incentivized reviews, review bombing | Review creation gated to `booking.status == COMPLETED` + one review per booking (unique constraint) + timing/pattern signals feeding `fraud_signals`. |
| T17 | Rate-sensitive endpoints | Scraping the search API to build a competing listing database, or abuse of booking-hold creation to lock out real users | Per-user + per-IP rate limits on `/search/parking` and `/bookings/holds`; hold TTL bounded so abusive hold-spamming self-heals within minutes; anomaly detection flags accounts creating holds far above normal booking-conversion rates. |
| T18 | Data at rest | Database/backup compromise exposing all PII/financial data at once | Encryption at rest (managed Postgres + S3 defaults), encrypted backups, least-privilege DB roles per service (the app's DB user cannot `DROP TABLE`/access unrelated schemas if this is later split), secrets never in the same store as the data they protect. |

---

## 14. Environment-Variable Specification

Grouped by concern; every secret-bearing variable is documented but never given a real value in source control (`.env.example` ships with placeholders only). "Unavailable-state behavior" describes what the app does when a variable is missing — required for every external-service credential per the spec.

### 14.1 Core / App
```
NODE_ENV=development|staging|production
PORT=3000
APP_BASE_URL=
CORS_ALLOWED_ORIGINS=
LOG_LEVEL=info
CORRELATION_ID_HEADER=x-correlation-id
```

### 14.2 Database / Cache
```
DATABASE_URL=postgresql://user:pass@host:5432/parkease
DATABASE_POOL_MAX=
REDIS_URL=redis://host:6379
```

### 14.3 Auth / Sessions
```
JWT_ACCESS_SECRET=            # or asymmetric key pair (JWT_ACCESS_PRIVATE_KEY/PUBLIC_KEY)
JWT_ACCESS_TTL_SECONDS=900
JWT_REFRESH_SECRET=
JWT_REFRESH_TTL_SECONDS=2592000
PASSWORD_HASH_ALGO=argon2id
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
```
*Unavailable state:* if `GOOGLE_OAUTH_CLIENT_ID` is unset, the "Sign in with Google" button is hidden server-side (a feature-flag-style capability check, `GET /auth/methods`), not shown-then-failing.

### 14.4 OTP / SMS
```
SMS_PROVIDER=                 # e.g. msg91 | twilio | gupshup
SMS_PROVIDER_API_KEY=
SMS_SENDER_ID=
OTP_LENGTH=6
OTP_TTL_SECONDS=300
OTP_MAX_ATTEMPTS=5
OTP_RESEND_COOLDOWN_SECONDS=30
```
*Unavailable state:* phone-OTP registration/login is disabled with a clear "Phone verification is temporarily unavailable, please use email login" message; never falls back to a fake/always-succeeds OTP.

### 14.5 Email
```
EMAIL_PROVIDER=                # e.g. ses | sendgrid | postmark
EMAIL_PROVIDER_API_KEY=
EMAIL_FROM_ADDRESS=
```

### 14.6 Payments
```
PAYMENT_PROVIDER=              # e.g. razorpay
PAYMENT_KEY_ID=
PAYMENT_KEY_SECRET=
PAYMENT_WEBHOOK_SECRET=
PAYMENT_ENV=test|live
```
*Unavailable state:* booking flow reaches "Confirm & Pay" and shows "Online payments are temporarily unavailable" with retry; cash-eligible listings still allow the cash flow if enabled; no booking is ever marked paid without a real gateway response.

### 14.7 Maps / Location
```
MAPS_API_KEY_ANDROID=          # restricted by package name + SHA-1
MAPS_API_KEY_SERVER=           # Geocoding/Places, IP/referrer restricted
```
*Unavailable state:* map view shows a "Map unavailable — showing list view" fallback; list view (which only needs lat/lng math, not the SDK) still works.

### 14.8 Push / Notifications
```
FCM_PROJECT_ID=
FCM_SERVICE_ACCOUNT_JSON=      # server-side, never shipped to client
```
*Unavailable state:* in-app notification center still populates from `notifications` table; push delivery is best-effort and its failure is logged/alerted, not surfaced as a user-facing error.

### 14.9 Storage
```
STORAGE_PROVIDER=              # s3 | gcs | minio(dev)
STORAGE_BUCKET_PUBLIC=
STORAGE_BUCKET_PRIVATE=
STORAGE_ACCESS_KEY_ID=
STORAGE_SECRET_ACCESS_KEY=
STORAGE_REGION=
STORAGE_SIGNED_URL_TTL_SECONDS=600
```

### 14.10 Observability
```
SENTRY_DSN_BACKEND=
SENTRY_DSN_ANDROID=
METRICS_ENDPOINT=
```

### 14.11 Feature/Business config (non-secret, but environment-scoped)
```
DEFAULT_COMMISSION_PERCENT=
DEFAULT_GST_PERCENT=
BOOKING_HOLD_TTL_SECONDS=600
INSTANT_PARKING_OWNER_RESPONSE_TIMEOUT_SECONDS=90
SUPPORT_CONTACT_EMAIL=
SUPPORT_CONTACT_PHONE=
PRIVACY_POLICY_URL=
TERMS_URL=
```

Every module in §3.2 that depends on an external credential (`payments/provider`, `notifications/provider`, `storage`) is written against an interface with an explicit "not configured" branch returning a typed `ServiceUnavailableError`, so missing configuration is a designed state, not an unhandled exception.

---

## 15. Android Permission Plan

| Permission | Requested when | Rationale shown to user | Denied/degraded behavior |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | User taps Find Parking / Nearby / Instant Parking / Navigation | "ParkEase needs your location to find nearby parking and give directions." | Falls back to manual address search; nearby/instant features show an explanatory empty state with a "Enable Location" CTA. |
| `CAMERA` | User opens QR scanner or "Add Photo" (owner listing/entrance photos) | "Camera access is needed to scan the parking QR code / take listing photos." | Manual booking-ID entry fallback for attendants where operationally reasonable; photo upload falls back to gallery picker. |
| `POST_NOTIFICATIONS` (Android 13+) | First time a notification-dependent action occurs (e.g., right after a booking is confirmed) | "Get notified about booking confirmations, entry/exit reminders, and payment updates." | App functions fully without it; in-app notification center remains available. |
| `READ_MEDIA_IMAGES` / legacy `READ_EXTERNAL_STORAGE` (API-gated) | Selecting an existing photo for upload | Standard picker rationale | Camera-only fallback. |
| Internet/Network state | Always (normal permission, no runtime prompt) | n/a | n/a |
| **No background location** | Not requested | Not justified by any implemented feature — Instant Parking and navigation are foreground/active-use only. | n/a |
| **No SMS read/receive** | Not requested | OTP is entered manually by the user (no auto-read), avoiding the sensitive-permission Play policy burden entirely. | n/a |

Permission requests use the standard "just-in-time, with rationale" pattern (a small explainer screen/dialog immediately before the OS prompt, per §"Location and Permissions" in the spec), and the app is designed so denial degrades a specific feature rather than blocking app usage overall.

---

## 16. Google Play Release Plan

**Package name:** `com.parkease.app` (placeholder pending final decision — see §18).
**Min SDK:** 26 (Android 8.0) — balances real-device reach in the India market against Compose/CameraX/library baseline requirements; revisit with Play Console device distribution data before Milestone 13.
**Target SDK:** must be Android 16 (API level 36), or whatever level Play requires at actual submission time, per the spec's "verify before final submission" instruction — this is re-checked as a Milestone 12/13 gate, not hard-coded now.

**Build variants:** `dev` (points at dev backend, debuggable, own applicationIdSuffix `.dev` so it installs alongside prod), `staging` (`.staging` suffix, points at staging backend, used for QA/pre-launch), `prod` (release backend, signed with the production keystore, App Bundle output).

**Signing:** production keystore generated and stored in the CI secret manager (never committed); Play App Signing enabled so Google holds the distribution key and we hold the upload key, per current best practice.

**Assets to prepare in Milestone 13:** app icon + adaptive icon, splash screen, feature graphic, phone/tablet screenshots (driver find/book/QR flow, owner listing/earnings flow, attendant scan flow), short + full store description, privacy policy URL (hosted, versioned), terms URL, Data Safety form answers (derived directly from §5's data model — location, phone, email, vehicle reg number, payment metadata, photos, KYC docs), content rating questionnaire answers, support email/contact.

**Reviewer access:** dedicated `dev`-role-tagged reviewer test accounts (driver, owner, attendant) seeded in the **staging** environment only, using the payment provider's test-mode credentials — reviewer instructions will explicitly document that a real payment cannot succeed without live provider approval and describe the test-mode flow instead. No production data or live payment credentials are ever in a reviewer account.

**What Milestone 13 will NOT claim:** automatic Play Store approval, or that legal/tax/business review has been done — those remain explicit go-live gate items (§17 of the original spec) requiring sign-off from the app owner and appropriate professionals.

---

## 17. Milestone Plan (recap)

Unchanged from the spec's structure — restated here so this document is the single source of truth for "what's next":

0. **Architecture & planning** *(this document)*
1. Project foundation — Android/backend scaffolding, DB migrations, Docker, CI, OpenAPI skeleton.
2. Authentication & roles — sessions, OTP/password/Google boundaries, RBAC enforcement.
3. Vehicles & categories — driver vehicle CRUD, category/type/size model.
4. Parking, sections & locations — owner listing creation, map location capture, approval flow.
5. Driver discovery — GPS flow, search/map/list, category filters, favorites.
6. Availability & booking — capacity model, holds, advance booking, Instant Mode, lifecycle.
7. Payments — provider integration, orders, webhooks, ledger (no simulated success).
8. QR & parking operations — digital pass, scan validation, check-in/out, mismatch workflow.
9. Earnings, refunds & settlements — owner ledger, commission, payouts, reconciliation basics.
10. Admin, fraud & support — dashboard, verification, config, tickets, disputes, audit viewer.
11. Notifications & analytics — push, history, deep links, category-specific analytics.
12. Release hardening — security/perf/accessibility review, full test suite, load/concurrency tests.
13. Play Store package — production config, legal URLs, Data Safety, store assets, signed AAB.

Each milestone will restate its objective, assumptions, architecture/data-flow decisions, security/privacy/payment/legal/Android risks, and acceptance criteria before any code is written, exactly as instructed, and will stop for your approval before the next one begins.

---

## 18. Open Questions Requiring Your Decision

These are the points where I made a reasonable default so planning could proceed, but the actual answer is a business/legal/product decision, not an engineering one. Please confirm or override before Milestone 1:

1. **Payment provider.** I've architected against a Razorpay-shaped interface (order + webhook + refund + route/transfer for owner payouts) since it's the most common India-marketplace fit, but the actual choice depends on your business entity type, whether you want Razorpay Route/split settlements vs. manual payout batching, and existing merchant relationships. This doesn't block Milestone 1–6 (booking/availability don't need a live payment integration), but it does block Milestone 7.
2. **Legal entity & GST registration status.** Tax/commission config (§5.9) is built to be fully configurable, but whether GST is even applicable at launch (turnover threshold, registration status) needs your input before Milestone 9's commission defaults are set.
3. **Package name / brand domain.** `com.parkease.app` is a placeholder — confirm the real reverse-domain identifier and whether `parkease.app`/`.in`/`.com` (or similar) is available for Play Console developer account, deep links, and the account-deletion web page.
4. **Space-level vs. section-level tracking as the default.** The spec supports both; I'm defaulting new listings to section-level (simpler owner onboarding) with space-level as an opt-in upgrade. Confirm that's the right default for your target owner segment (e.g., large mall operators may want space-level from day one).
5. **Instant Parking request-workflow scope for Milestone 6.** The spec describes both an "instant book when Instant Mode is ON" path and an optional "request workflow with owner timeout" when it's OFF. I plan to build the instant-book path fully in Milestone 6 and treat the request/timeout workflow as an explicitly flagged stretch item within that milestone (behind a feature flag) rather than blocking the milestone on it — confirm that's acceptable or if it should be pulled forward/back.
6. **SMS/Email provider.** No preference stated; I'll default to a provider with strong India delivery rates (e.g., MSG91 for SMS) unless you have an existing account with one.
7. **Attendant employment model.** Are attendants always hired directly by an owner (per-listing assignment, as modeled), or does ParkEase itself ever employ/dispatch attendants centrally? This affects `attendant_profiles.employer_owner_id` nullability and admin-assignment permissions.
8. **Min SDK 26 (Android 8.0).** Confirm this matches your target device reach in the Indian market, or whether it should go lower (more legacy-device reach, but drops some modern-library conveniences) or higher.
9. **Reviewer/business go-live timeline vs. this milestone cadence.** The full plan (0–13, each requiring your explicit approval) is thorough but sequential. Let me know if you want any milestones combined/parallelized once we're past the payment/booking core, to hit a target launch date.

---

*End of Milestone 0. No application code has been written. Awaiting your approval (and answers to §18, where you have a preference) before starting Milestone 1.*
