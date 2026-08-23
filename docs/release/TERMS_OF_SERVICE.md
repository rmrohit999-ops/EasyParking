# ParkEase Terms of Service

**Status: template — ready to publish once the bracketed placeholders below are filled in.**
This document reflects what the *actual, built* ParkEase app and backend do, as of Milestone 13.

*Last updated: [DATE]*

## 1. Who these terms are between

These Terms of Service ("Terms") are an agreement between you and [LEGAL ENTITY NAME]
("ParkEase", "we", "us"), the operator of the ParkEase mobile app and the parking marketplace
it connects to. By creating an account or using the app you agree to these Terms. If you don't
agree, don't use the app.

## 2. What ParkEase is

ParkEase is a marketplace that connects drivers looking for parking with parking owners who
list spaces. Four kinds of accounts exist:

- **Driver** — searches for and books parking, pays for bookings, adds vehicles.
- **Parking owner** — lists parking locations and sections, sets pricing/availability, receives
  payouts for completed bookings, employs attendants.
- **Attendant** — staff of a parking owner; checks vehicles in/out at a location via QR scan,
  handles on-site cash collection where enabled.
- **Admin** — ParkEase staff who moderate listings, resolve disputes, and operate the platform.

**ParkEase is a marketplace, not the parking operator.** The parking owner is responsible for
the physical space, its safety, and its condition. ParkEase is not liable for damage to, or
loss of, your vehicle or its contents while parked — that liability, to the extent any exists,
sits with the parking owner and/or their insurance, subject to applicable law.

## 3. Eligibility and accounts

You must be at least [18 / the age of majority in your jurisdiction] to create an account. You
are responsible for the accuracy of the information you provide (including vehicle details and,
for owners, listing details and payout account information) and for keeping your login
credentials secure. One account per person; role assignment (driver/owner/attendant/admin) is
managed by ParkEase or, for attendants, by the owner who employs them.

## 4. Bookings, vehicle-category matching, and check-in/out

Every parking section is designated for a specific vehicle category (two-wheeler or
four-wheeler) and, within that, specific vehicle types. ParkEase enforces this at booking time —
you can only book a section your vehicle is compatible with — and again at check-in, where an
attendant verifies your vehicle against your booking before letting you in. A booking reserves
capacity in a section for a time window (scheduled) or from the moment of instant approval
(instant booking); showing up outside your booked window, or without a valid QR pass, may result
in denied entry or additional charges per the owner's posted terms.

## 5. Payments, pricing, and fees

Prices are set by the parking owner and shown to you before you confirm a booking, inclusive of
any applicable taxes and the ParkEase service component as displayed at checkout. Payments are
processed by our payment gateway partner (Razorpay); **ParkEase's servers never receive or store
your raw card, UPI, or bank credentials.** Some locations may support paying an attendant in
cash on-site instead of paying in-app — where that's offered, the app will say so.

## 6. Cancellations and refunds

Cancellation and refund terms depend on how far ahead of your booking's start time you cancel,
and on the booking type (scheduled vs. instant), as shown in the app at the time of booking and
cancellation. Refunds, when due, are issued to your original payment method and may take several
business days to appear depending on your bank/payment provider. Disputed charges can be raised
through in-app Support; ParkEase reviews the booking, payment, and check-in/out record and makes
a determination, which may include a partial or full refund at ParkEase's discretion where the
facts warrant it.

## 7. For parking owners: listings, payouts, and settlement

If you list parking as an owner, you're responsible for the accuracy of your listing (location,
capacity, vehicle categories/types accepted, pricing, photos) and for the parking space matching
what's described. Earnings from completed bookings, less ParkEase's commission and applicable
taxes, are settled to the payout account (bank account or UPI) you provide, which we encrypt at
rest and only decrypt at the moment a payout is dispatched through our payout partner
(RazorpayX). You're responsible for the accuracy of your payout details — payouts sent to an
incorrect account you provided cannot always be recovered. Repeated cancellations, no-shows on
confirmed bookings, or listing misrepresentation may result in suspension of your listing or
account per ParkEase's fraud-prevention policies.

## 8. For attendants

If you're designated an attendant by a parking owner, your access to check-in/check-out tools
and any cash-handling features is scoped to that owner's location(s) and can be revoked by the
owner or ParkEase at any time. You're expected to accurately record vehicle check-in/check-out
and any cash collected.

## 9. Prohibited conduct

You agree not to: provide false vehicle, identity, or listing information; attempt to bypass
vehicle-category/type checks; interfere with another user's booking or vehicle; attempt to
manipulate pricing, availability, or the payment/refund system; harass other users, owners, or
attendants; or use the app for any unlawful purpose. ParkEase may suspend or terminate accounts
that violate these Terms, including based on fraud-detection signals such as repeated no-shows or
payment tampering.

## 10. Reviews and disputes

Reviews you post are your own statements and must be honest; ParkEase may remove reviews that
violate applicable law or that are clearly abusive. Disputes over a specific booking (e.g.
charged incorrectly, space unavailable as described, damage claim) can be raised via in-app
Support/Disputes; ParkEase will investigate using the booking, payment, and check-in/out records
available to it and issue a resolution, which may include a refund adjustment.

## 11. Disclaimers and limitation of liability

ParkEase provides the app "as is." To the maximum extent permitted by applicable law, ParkEase
is not liable for indirect, incidental, or consequential damages, or for the acts or omissions
of parking owners, attendants, or other users, including damage to or theft of your vehicle
while parked. Nothing in these Terms limits liability where applicable law does not allow it
to be limited (for example, liability for ParkEase's own fraud or gross negligence).

## 12. Changes to these Terms

We may update these Terms from time to time; material changes will be announced in-app and take
effect on the date stated. Continued use of the app after that date means you accept the updated
Terms.

## 13. Governing law and disputes

These Terms are governed by the laws of [JURISDICTION], without regard to conflict-of-law rules.
[DISPUTE RESOLUTION MECHANISM — e.g. courts of X / arbitration per Y].

## 14. Contact

[LEGAL ENTITY NAME]
[REGISTERED ADDRESS]
[CONTACT EMAIL]

See also our [Privacy Policy](./PRIVACY_POLICY.md).

---
*Maintainer note: generated from a review of the actual app functionality (booking/payment/
refund/settlement/dispute state machines, role model, vehicle-category enforcement) as of
Milestone 13. Before publishing: fill in every `[BRACKETED]` placeholder, have it reviewed by
counsel for your actual jurisdiction(s) — especially §11 (liability) and §13 (governing law/
dispute resolution), which are jurisdiction-sensitive and must not ship with placeholder text —
and host it at a stable HTTPS URL alongside the Privacy Policy.*
