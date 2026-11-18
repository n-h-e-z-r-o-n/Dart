# Storefronts, appointment requests and inquiries

## Apply to the existing project

Run **011_storefront_bookings.sql** once, in full, in Supabase SQL Editor after your existing 008 migration. It is transactional and reloads the API schema. Do not rerun foundation or wallet migrations. It does not activate external payments or require an Edge Function, new secrets or cron.

Install the updated Android build. Business dashboard → **Storefront, bookings & inquiries**:

1. Owners/managers use **Manage services** to add names, descriptions, KES prices and estimated durations. Hide a service to stop new requests without deleting its history. Up to 200 services per business.
2. **Public details & request settings** controls the public phone, email, HTTPS website, opening hours, IANA business time zone and up to eight public HTTPS photo links. The first photo is the cover. Image loading has a 4 MB download limit and sampled decoding; failed images show a fallback. Uploading photos from the device is not included; use publicly hosted image URLs.
3. Publish the business through the existing listing controls. Customers can then discover it and request an appointment or send an inquiry. Default time zone is Africa/Nairobi; set the actual business zone before taking requests.

## Customer flow

Explore → business storefront → service → preferred date/time and message → sign in if necessary → review → send → **Awaiting business confirmation**. The request does not reserve a unique seat or time slot. Different customers may choose the same time, and the business may confirm all of them if it has capacity. Customers and businesses see times in the business time zone. The time picker uses a 24-hour clock. Times with ambiguous or nonexistent daylight-saving offsets are rejected; choose another time.

Guests retain their service/time/message through sign-in. Opening My bookings while signed out continues to that inbox after authentication. A signed-in user's drafts are cleared when the public feature binds a different identity. No booking is submitted automatically after sign-in.

Account → **My bookings & inquiries** shows status, replies, history and cancellation. Requests and inbox detail refresh while foregrounded; manual refresh is always available. Lists use stable cursor pagination (30 per page). Status filters (All, Pending, Confirmed, Completed, Closed) run in the database before pagination, including older matching requests. No push notification or reminder delivery is claimed by this release: customers must open the app to see updates.

## Business decisions

Owners/managers accept or decline appointment requests, answer inquiries, cancel confirmed appointments with a reason, and mark confirmed appointments complete or record a no-show after the requested start time. Staff can read their business inbox and history, but cannot edit the catalogue/settings or confirm/cancel requests. Team payment-release permission remains a separate capability.

Pausing new requests keeps existing appointments and inquiries accessible. Unpublished/suspended listings cannot receive new requests or new confirmations. Existing requests remain accessible to their customer and business team so they can cancel or resolve them. Service changes/hiding do not rewrite an existing request's quoted service name, duration or price. A price change between selection and submission requires a fresh customer review.

To request a different time, cancel the old request with a reason and submit a new one. There is no silent rescheduling of confirmed appointments. Confirmations never assume how many employees or seats a business has. No automatic expiry or capacity cap is imposed. Pending requests whose preferred time has passed can still be declined/cancelled, but cannot be newly confirmed.

## Payments and security

Booking requests and inquiries never debit a wallet, create payment obligations or release escrow. Existing **Services & payments** handles separately reviewed service-payment requests. Cancelling a booking does not automatically refund a payment; use the existing refund/dispute flow. Booking records preserve the original quoted price for reference.

All tables are private, RLS-enabled and unavailable to app roles for direct access. Narrow security-definer RPCs validate identity, membership, input and state transitions. Event history is append-only through these RPCs. Customers can read only their own requests; team members can read only their business inbox. Anonymous users see published storefront data only. Requests require authentication, have stable retry references, and are limited to 20 newly created requests per customer per hour. Changes to retry payloads are rejected rather than creating ambiguous duplicates.

## Verification

Run **012_storefront_booking_checks.sql** only on a development project; it inserts test users and rolls all fixtures back. It checks anonymous access, tenant isolation, staff restrictions, price snapshots, same-time requests/confirmations, retry safety, inquiry replies, cancellation reasons and separation from wallet money.

The local PostgreSQL concurrency harness additionally exercises simultaneous requests from different customers for the same preferred time and simultaneous duplicate retries. Android tests cover both audience journeys. Hosted migration application and a genuine customer/business account smoke check remain deployment steps; local tests do not apply this migration to your project.
