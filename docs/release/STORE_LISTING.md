# Play Store listing — ParkEase

Everything needed to fill in Play Console's "Store presence → Main store listing" page, plus the
Content rating questionnaire and Store settings (category/contact/tags). Text fields are ready to
paste; asset fields describe what's needed and how to produce it, since no visual assets have
been generated yet (see §4).

## 1. App details

- **App name:** ParkEase
- **Short description** (max 80 characters):
  `Find and book parking in real time — for two-wheelers and cars.`
  (66 characters)
- **Full description** (max 4000 characters):

  ```
  ParkEase — Find Parking. Park Easy.

  Stop circling the block. ParkEase shows you real parking availability nearby, matched to
  your exact vehicle — two-wheeler or four-wheeler — so you never book a spot your vehicle
  can't actually use.

  FOR DRIVERS
  • Search nearby parking with live availability, by vehicle type
  • Book ahead for a scheduled time, or use Instant Parking to book on arrival
  • Pay securely in-app, or pay cash on-site where the location allows it
  • Get a QR pass for fast check-in and check-out — no paper tickets
  • Track your booking history, cancellations, and refunds in one place
  • Raise a support ticket or dispute directly from any past booking

  FOR PARKING OWNERS
  • List your parking location and sections in minutes — by vehicle category and type
  • Set your own pricing and availability
  • Track bookings, occupancy, and earnings in real time
  • Get paid out automatically to your bank account or UPI, on your settlement schedule
  • Add attendants to manage check-in/out at your location, with scoped access you control

  FOR ATTENDANTS
  • Scan a driver's QR pass to check vehicles in and out
  • See booking and vehicle details at a glance before letting a vehicle in
  • Record on-site cash payments where enabled

  BUILT FOR TRUST
  • Vehicle-category matching means you're never shown — or don't accidentally book — a spot
    that doesn't fit your vehicle
  • Payments are processed by a licensed payment gateway; ParkEase never sees your card or
    bank details
  • Every booking, check-in, and payment is recorded, so disputes have a clear record to
    resolve from

  Whether you're looking for a place to park or a way to earn from a space you own, ParkEase
  makes it simple, transparent, and fast.
  ```

  (~1,650 characters — well under the 4000 limit; expand with real screenshots' captions or
  regional details once available, but don't pad with keyword-stuffed filler — Play's review
  process flags that.)

- **App category:** Maps & Navigation, or Travel & Local (pick whichever Play Console's current
  category list nearest matches "local parking marketplace" at submission time — confirm in the
  live console, categories are occasionally renamed).
- **Tags:** parking, parking finder, book parking, two-wheeler parking, car parking (choose up
  to 5 from Play Console's controlled tag list at submission time).
- **Contact details:**
  - Email: [SUPPORT_CONTACT_EMAIL — matches `SUPPORT_CONTACT_EMAIL` in `.env.example`]
  - Phone (optional): [SUPPORT_CONTACT_PHONE]
  - Website (optional): [https://parkease.app]
- **Privacy policy URL:** the hosted URL for `docs/release/PRIVACY_POLICY.md` (see that file's
  maintainer note — must be filled in and hosted before this field can be completed).

## 2. Content rating questionnaire — guidance

Play's IARC questionnaire asks category-by-category yes/no questions; answer based on what the
app actually contains, not what a "parking app" typically contains:

- **Violence:** No.
- **Sexuality:** No.
- **Language:** No (no user-generated profanity filtering exists because there's no open chat —
  support/dispute messages and reviews are free text between two identified parties, not public;
  answer per Play's actual wording for "user-generated content" honestly — reviews are visible to
  other users, so mark user-generated text content as present where the questionnaire asks).
- **Controlled substances:** No.
- **Gambling:** No — ParkEase is a service marketplace with fixed/owner-set pricing, not wagering.
- **User-generated content:** Yes — reviews and (between the two parties involved) support/dispute
  messages. No public chat or open forum exists.
- **Shares location:** Yes — precise, foreground-only, permission-gated (see DATA_SAFETY.md).
- **Digital purchases / real-money transactions:** Yes — booking payments are real money via
  Razorpay.

Answer the actual live questionnaire directly in Play Console at submission time — the categories
above are current as of writing but IARC's question wording is Google's to change.

## 3. Data safety

Filled in from `docs/release/DATA_SAFETY.md` — that document is the source of truth for every
answer in Play Console's Data Safety form; don't re-derive it from scratch there.

## 4. Visual assets needed (not yet produced)

None of these exist yet. Each needs to be produced from real app screens (once there's a build to
screenshot) or commissioned graphic work — none should be AI-generated stock imagery presented as
the app's real UI, since Play reviewers check screenshots against the actual APK.

| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG, 32-bit with alpha | ❌ not created — `app/src/main/res/mipmap-*` currently uses the default AGP template launcher icon; needs a real ParkEase mark before submission |
| Feature graphic | 1024×500 PNG/JPEG, no alpha | ❌ not created |
| Phone screenshots | 2–8 images, 16:9 or 9:16, min 320px/max 3840px on the short side | ❌ not created — capture from a real signed build once one exists: driver search/map, booking detail with QR pass, owner listing/earnings dashboard, attendant scan screen |
| 7-inch / 10-inch tablet screenshots | Optional but recommended if layouts are responsive | ❌ not evaluated — the app has not been tested on tablet form factors; either verify it renders acceptably or skip tablet screenshots and let Play show phone screenshots scaled |
| Promo video (optional) | YouTube URL | ❌ not created |

Suggested screenshot order/captions once captured: (1) search/map view — "Find parking near you,
matched to your vehicle", (2) booking confirmation with QR — "Book ahead, or park instantly", (3)
owner earnings dashboard — "List your space, get paid automatically", (4) attendant scan screen —
"Fast, contactless check-in".

## 5. Release track recommendation

Start on **Internal testing** (fastest review, for the team + REVIEWER_INSTRUCTIONS.md accounts),
then **Closed testing** with a small external group once payments/payouts are verified working
end-to-end against Razorpay test mode, then **Production** only after:
- The account-deletion UI gap (DATA_SAFETY.md §Data deletion) is closed, or the Data Safety form
  and Play's account-deletion policy are reconciled another way.
- Real app icon + feature graphic + screenshots exist (§4).
- Privacy Policy and Terms are hosted at stable HTTPS URLs with brackets filled in.
- Release signing (`docs/RELEASE_SIGNING.md`) is exercised at least once and the resulting build
  installs and runs correctly on a real device.

---
*Maintainer note: text fields (description, category guidance) are ready to use; asset fields are
honestly marked not-yet-produced rather than described as if they exist — see the go-live
checklist for these as concrete remaining tasks.*
