# Play Console Data Safety form — ParkEase answers

**Status: working reference for whoever fills in the Play Console "Data safety" section.**
This maps the *actual, built* data practices (same source review as PRIVACY_POLICY.md) onto the
Play Console form's specific categories as of the current Play Console structure. Google
periodically revises the form's wording/categories — cross-check this against the live console
before submitting, but the underlying facts here (what ParkEase actually collects/shares/for
what purpose) will still be correct.

## Does your app collect or share any of the required user data types?

**Yes.**

## Data types collected, by Play Console category

### Personal info

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Name | Optional (Google sign-in) | No | Account management | Only if the user signs in with Google and their Google profile has a name |
| Email address | Yes | Yes (payment/OTP/email providers, as needed to deliver the service) | Account management, app functionality | Used for login and account communications |
| Phone number | Yes | Yes (SMS/OTP provider, as needed to deliver OTP) | Account management, app functionality | Used for login/OTP |
| Address | No | — | — | Not collected — no home/billing address field exists |
| User IDs | Yes | No | Account management, app functionality | Internal account ID; Google subject ID if using Google sign-in |
| Other info (role) | Yes | No | App functionality | Driver/owner/attendant/admin role flag |

### Financial info

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Payment info | **No** (see note) | — | — | ParkEase's own servers never receive/store raw card/UPI/bank credentials — entered directly with Razorpay. Answer "not collected" for this sub-item, since the app process never handles it. |
| Purchase history | Yes | No | App functionality, analytics (internal only) | Booking/payment amount and status records |
| Other financial info (owner payout account) | Yes | Yes (RazorpayX, payout provider) | App functionality | Bank account/IFSC or UPI ID, collected only from owner-role accounts; encrypted at rest (AES-256-GCM) |

### Location

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Approximate location | No | — | — | App uses precise, not approximate, location for search |
| Precise location | Yes, **only with permission, foreground-only** | No | App functionality (find nearby parking) | Not collected in the background; single on-demand fix per search, not continuous tracking. In the Play Console flow, answer "collected" but mark it not shared and explain foreground-only usage in the optional notes field. |

### Photos and videos

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Photos | Yes (owner-role only) | No | App functionality | Listing/section photos an owner voluntarily uploads. QR scanning at check-in/out is on-device and does NOT upload camera images — only the resulting event (a check-in/out record) reaches the server, so answer "not collected" for the attendant QR-scan flow specifically. |
| Videos | No | — | — | Not collected |

### Audio files

Not collected — the app does not use the microphone.

### Files and docs

Not collected beyond the listing photos captured above.

### Calendar

Not collected.

### Contacts

Not collected — the app never requests contacts permission.

### App activity

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| App interactions | **No** (currently) | — | — | Firebase Analytics dependency is present but not initialized/collecting as of this build. Re-answer this section the moment Analytics is turned on. |
| In-app search history | Yes (server-side only, for booking/search records) | No | App functionality | Search/booking queries used to serve results; not used for ads |
| Other user-generated content (support/dispute messages, reviews) | Yes | No (visible only to the relevant counterparty/support) | App functionality | |

### Web browsing

Not collected — no in-app browser/WebView tracking.

### App info and performance

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Crash logs | **No** (currently) | — | — | Firebase Crashlytics dependency present but not initialized. Re-answer the moment it's turned on — crash logs would then be "Collected, not shared, App functionality." |
| Diagnostics | No | — | — | |

### Device or other IDs

| Data type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Device or other IDs | Yes | No | Fraud prevention, security | FCM push token and a device identifier logged with login sessions for security/audit and to enable push delivery |

## Security practices section

- **Is all user data encrypted in transit?** Yes — HTTPS/TLS for all API traffic.
- **Do you provide a way for users to request data deletion?** Yes — via in-app Support / the
  contact email in the Privacy Policy. Answer "Yes."
- **Is data collection optional for any of the above?** Location is optional (permission-gated,
  search still works via manual address entry without it); push notification registration is
  optional (notification categories can be disabled, though the underlying token registration
  itself is an on/off system permission).

## Data deletion

The backend has a real, working self-serve endpoint (`DELETE /v1/auth/account`) that deletes/
anonymizes the account (legally-retained transaction records are preserved per the Privacy
Policy's retention rules). **However, as of Milestone 13 no Android screen calls it** — there is
no "Delete my account" button anywhere in the app yet, so the capability exists end-to-end at
the API layer but is not reachable by an actual user through the UI. Google Play's Account
Deletion policy requires an in-app (or linked web) path a user can actually use, not just an API
that exists — so this is a real gap, not just a documentation nicety. Until a UI is added,
answer the Play Console form truthfully as "deletion available via support request" (contact
email in the Privacy Policy) and treat "wire the existing DELETE /v1/auth/account endpoint to a
Settings screen" as a go-live blocker — see the go-live checklist.

---
*Maintainer note: this mapping was built by re-deriving each Play Console category from the same
schema/code review used for PRIVACY_POLICY.md, not by guessing generic answers. Two items need a
follow-up the moment they change: (1) Firebase Analytics/Crashlytics — currently answered "not
collected" because they are genuinely inactive; the form MUST be updated the same day they're
initialized, or the Data Safety declaration becomes false, which is a Play Store policy
violation. (2) The account-deletion path — currently support-mediated; if/when a self-serve
in-app deletion flow ships, update this doc and the Play Console form together.*
