# Google Maps API quota safety — ParkEase

Referenced from `parkease-backend/src/modules/maps-quota/`. This document explains the two
independent layers that keep Google Maps Platform billing at ₹0 for ParkEase, and how to set up
the one piece that lives outside this repo (Google Cloud Console itself).

## Why two layers, not one

1. **Backend soft breaker** (`MapsQuotaService`) — an early-warning circuit breaker that tracks
   requests per billable SKU in Redis and blocks further calls once a configurable daily safety
   cap is hit, alerting the Super Admin. This is *our* code, so it's only as reliable as this
   service staying up and Redis staying reachable.
2. **Google Cloud Console hard quota** (this doc, section 2) — a real, Google-enforced daily
   request cap per API, configured directly in your Cloud project. This is the actual backstop:
   it holds even if our backend is down, misconfigured, or has a bug.

Set up both. Neither replaces the other.

## Current status (read this before configuring anything)

As of this writing, **ParkEase makes zero billable Google Maps Platform requests** — no
Directions API, no Places Autocomplete, no server-side Geocoding API call exists anywhere in the
codebase yet. `MapsQuotaService` and this runbook exist ahead of that need, so that whichever of
those features gets built first already has a working breaker to wrap around it — see the doc
comment at the top of `maps-quota.service.ts`.

Two things that are *not* covered by any of this, because they're not billed by Google Maps
Platform at all:
- **Map rendering** (`core-maps`'s `LocationPickerMap`/`MarkersMap`, using the Maps SDK for
  Android) — free and unlimited on native mobile SDKs.
- **Reverse geocoding** in `LocationFormScreen` — uses Android's own on-device `Geocoder`, a free
  system service, not Google's paid Geocoding API.

## 1. Backend soft breaker — configuration

Two env vars (see `.env.example`):

- `MAPS_QUOTA_DAILY_SAFETY_CAP` (default `260`) — the daily request cap per SKU
  (directions/places/geocoding) that `MapsQuotaService.checkAndIncrement()` enforces. Default is
  80% of Google's ~10,000/month free tier, spread across 30 days
  (`10000 × 0.8 ÷ 30 ≈ 266`, rounded down to 260).
- `MAPS_BUDGET_WEBHOOK_SECRET` — a random secret string (`openssl rand -hex 32`) that section 3
  below uses to authenticate a real Google Cloud Billing Budget alert back into this backend.

`GET /v1/admin/maps-quota` (ADMIN/SUPER_ADMIN) returns today's usage per SKU — this is what the
Admin dashboard's quota screen reads.

## 2. Google Cloud Console — hard daily quotas

For each of the three APIs your project will eventually enable:

1. Go to **Google Cloud Console → APIs & Services → [Directions API / Places API / Geocoding
   API] → Quotas**.
2. Find the **Requests per day** quota.
3. Click **Edit Quotas**, set it to **260** (or whatever you set `MAPS_QUOTA_DAILY_SAFETY_CAP`
   to), and submit. Google enforces this at their edge — once hit, further requests fail with
   `OVER_QUERY_LIMIT` regardless of what our backend thinks its own counters say.

Repeat for each API as it's actually enabled on the project — don't set quotas for APIs you
haven't turned on yet.

## 3. Google Cloud Billing Budget alert → our webhook (optional, extra layer)

This wires a real Cloud Billing spend alert into `POST /v1/admin/maps-quota/budget-alert-webhook`,
which force-trips the breaker globally the moment your *actual* Cloud Billing spend crosses a
threshold — independent of whatever our own request counters say, so a bug in our counting logic
can't blind this layer.

1. **Billing → Budgets & alerts → Create budget.** Scope it to this project (or a label covering
   just the Maps APIs, if you share a project with other billed services). Set the budget amount
   to whatever your real ceiling is (₹1 is a reasonable "alert on any charge at all" budget for a
   project meant to stay ₹0).
2. Under **Manage notifications**, enable **Connect a Pub/Sub topic** and create/select a topic
   (e.g. `maps-budget-alerts`).
3. Deploy a small Cloud Function (or Cloud Run service) subscribed to that Pub/Sub topic, whose
   only job is to forward the event as an HTTP POST to:
   ```
   POST https://<your-backend-host>/v1/admin/maps-quota/budget-alert-webhook
   X-Webhook-Secret: <MAPS_BUDGET_WEBHOOK_SECRET>
   ```
   (Pub/Sub itself doesn't do outbound webhooks directly — the Cloud Function is the bridge. A
   minimal one is ~10 lines: read the Pub/Sub push message, POST to the URL above with the
   header, done.)
4. Set the Cloud Function's own `MAPS_BUDGET_WEBHOOK_SECRET` env var to the same value as the
   backend's, so it can send the header.

Until this is set up, `MAPS_BUDGET_WEBHOOK_SECRET` stays unset and the webhook endpoint responds
`503` to any request — it never silently accepts an unauthenticated trip of the breaker.

## Verifying it works

- `curl https://<backend>/v1/admin/maps-quota -H "Authorization: Bearer <admin token>"` — should
  return all three SKUs at 0% until real traffic exists.
- `curl -X POST https://<backend>/v1/admin/maps-quota/budget-alert-webhook -H "X-Webhook-Secret:
  <secret>"` — should return `{"received": true}` and immediately make `getUsageSnapshot()` report
  `globallyTripped: true` for the rest of the day.
