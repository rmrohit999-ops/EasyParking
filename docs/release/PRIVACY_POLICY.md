# ParkEase Privacy Policy

**Status: template — ready to publish once the bracketed placeholders below are filled in.**
This document describes what the *actual, built* ParkEase app and backend collect, as of
Milestone 13, not a generic boilerplate policy. If a future change adds new data collection
(a new permission, a new third-party SDK, a new field on a sign-up form), this document must
be updated in the same change — see the note at the bottom.

*Last updated: [DATE]*

## Who we are

ParkEase ("we", "us", "our") operates the ParkEase mobile app and the parking marketplace it
connects to. This policy explains what personal data ParkEase collects, why, how it's used,
who it's shared with, and the choices you have.

- **Data controller:** [LEGAL ENTITY NAME], [REGISTERED ADDRESS]
- **Contact:** [PRIVACY CONTACT EMAIL]
- **Grievance officer (India, per IT Rules 2021):** [NAME, EMAIL, ADDRESS]

## What we collect

### Account information
When you register, we collect your phone number and/or email address, and — if you sign up
with a password — a securely hashed (Argon2id) version of it; we never store your password
in readable form. If you sign in with Google, we receive a Google-issued subject identifier
and, depending on your Google account settings, your name/photo. We also store which of the
four roles (driver, parking owner, attendant, admin) your account holds.

### Vehicles
If you use ParkEase as a driver, we store the vehicles you add: category (two-wheeler /
four-wheeler / other), type (bike, scooter, car, etc.), registration number, and optionally
make/model. This is what lets us show you parking that's actually compatible with your
vehicle, and what an attendant checks against at entry.

### Location
With your permission, we use your device's GPS location to find parking near you and to show
distance/directions. Location is only read when you're actively using the search feature
(a single on-demand fix, not continuous background tracking) — ParkEase does not request or
use background location access. You can deny or revoke this permission at any time in Android
Settings; search still works with manual address entry.

### Bookings, payments, and parking activity
We store your booking history (which parking section, vehicle, times, status), QR
check-in/check-out records, and payment records (amount, gateway transaction reference,
status). **Card, UPI, and net-banking details you use to pay are handled entirely by our
payment gateway partner (Razorpay) — ParkEase's own servers never receive or store your raw
payment credentials.** If you pay a parking attendant in cash, that cash collection is
recorded (amount, who collected it, when).

### Parking owner payout details
If you list parking as an owner, we collect your bank account number/IFSC or UPI ID so we can
pay out your earnings. These are encrypted at rest (AES-256-GCM) in our database and are only
ever decrypted at the moment a payout is actually dispatched to our payout provider
(RazorpayX). We display only a masked version (e.g. last 4 digits) back to you in the app.

### Camera and photos
If you're a parking owner, you can photograph your listing and its sections. If you're an
attendant, the app uses your camera to scan a driver's QR pass at check-in/check-out — the
scan itself happens on-device; only the resulting check-in/check-out event is sent to our
servers, not a copy of the camera feed or a photo of the QR code.

### Push notifications
If you allow notifications, we register your device's Firebase Cloud Messaging token so we
can send you booking, payment, refund, payout, and support updates. You can turn individual
notification categories off in the app (Notifications → Preferences) — every update is still
recorded in your in-app notification inbox even if push is off, so you never lose the
record, only the alert.

### Support, disputes, and reviews
Messages you send to support, evidence and explanations you submit for a dispute, and reviews
you leave are stored as-is, attributed to your account, so the other party and our support
team can see them.

### Device and security information
For fraud prevention and account security, we log IP address, a device identifier, and basic
device info (from your login session), and keep an audit trail of account-security-relevant
actions (sign-in, password reset, role changes, admin actions on your account). Failed
sign-in and rate-limit-triggering requests are also logged for abuse prevention.

### What we do **not** currently collect
Crash reporting (Firebase Crashlytics) and analytics (Firebase Analytics) libraries are
present in the app but are **not yet initialized or sending data** — ParkEase does not
currently collect crash reports or product-analytics events. This policy will be updated
before that changes. We also do not read your contacts, SMS messages, or call log, and we
do not request background location.

## Why we use your data

- To create and secure your account, and verify who's making a booking/payment/payout.
- To match your vehicle to compatible parking and show you nearby availability.
- To process bookings, payments, refunds, and owner payouts.
- To let attendants verify a driver's booking at check-in/check-out.
- To send you the notifications you've opted into, and to respond to support requests and
  disputes.
- To detect and prevent fraud and abuse (e.g. repeated no-shows, payment tampering).
- To comply with legal and tax obligations related to payments processed through the
  platform.

## Who we share it with

- **Razorpay** (payment gateway) and **RazorpayX** (payout provider) — to process your
  payments and, if you're an owner, your payouts. They receive what's needed to process the
  transaction (amount, your payment instrument details entered directly with them, your
  payout destination).
- **Google Firebase Cloud Messaging** — to deliver push notifications, given your device's
  registration token.
- **[SMS/OTP PROVIDER NAME]** and **[EMAIL PROVIDER NAME]** — to deliver one-time passcodes
  and account emails.
- **Law enforcement or regulators**, only when legally required.
- We do **not** sell your personal data, and we do not share it with advertisers.

## Your choices and rights

- **Access/export:** you can request a copy of the personal data we hold about you.
- **Correction:** you can update most account/vehicle details directly in the app.
- **Deletion:** you can request account deletion; we'll delete or anonymize your personal
  data except where we're required to retain records (e.g. completed transaction records,
  for tax/audit purposes, per [RETENTION PERIOD] as required by [APPLICABLE LAW]).
- **Notification preferences:** control per-category push notifications in-app at any time.
- **Location:** deny/revoke the location permission in Android Settings at any time.

To exercise any of these, contact [PRIVACY CONTACT EMAIL].

## Data retention

We retain account and transaction data for as long as your account is active and, after
deletion, for [RETENTION PERIOD] where required for legal, tax, dispute-resolution, or fraud-
prevention purposes. Encrypted payout account details are deleted when you remove that payout
method from the app.

## Children's privacy

ParkEase is not directed at children and is not intended for use by anyone under [18 / the
age of majority in their jurisdiction]. We do not knowingly collect personal data from
children.

## Security

Passwords are hashed with Argon2id, sensitive payout details are encrypted at rest, all
network traffic uses HTTPS/TLS, and access to production data is role-restricted and
audit-logged. No system is perfectly secure; if you believe your account has been
compromised, contact us immediately at [SECURITY CONTACT EMAIL].

## Changes to this policy

We'll update this page when what we collect or how we use it changes, and update the "Last
updated" date above. Material changes will also be announced in-app.

## Contact us

[LEGAL ENTITY NAME]
[REGISTERED ADDRESS]
[PRIVACY CONTACT EMAIL]

---
*Maintainer note: this document is generated from a review of the actual Prisma schema and
service code as of Milestone 13. Before publishing, fill in every `[BRACKETED]` placeholder,
have it reviewed by counsel for your actual jurisdiction(s), and host it at a stable HTTPS
URL — that URL is what goes into the Play Console "Privacy policy" field (see
STORE_LISTING.md) and into the app's own Settings screen once one exists.*
