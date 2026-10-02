# BookingHotel2 — Combined Phase 1 + 2 + 3 + 4 patch (v1.0.0)

This patch is designed to be copied over the exact BookingHotel2 baseline supplied for this work. It includes the Phase 1 lifecycle hardening plus the Phase 2, 3 and 4 changes, so it should be applied once rather than layering the older Phase 1 ZIP first.

## What changed

### Phase 1 — Booking & Payment lifecycle

- Failed/expired reservations return inventory exactly once through `inventory_released`.
- `PENDING_PAYMENT` / `PROCESSING` reservations receive an expiry deadline.
- A scheduler expires abandoned reservations.
- Booking transitions are centralized in `BookingLifecycleService`.
- Retrying a failed booking re-reserves inventory under a pessimistic room lock.
- VNPAY keeps a short confirmation grace window for delayed IPN callbacks.
- Duplicate terminal callbacks cannot restore inventory twice.

Backend migration involved: `V4__booking_reservation_lifecycle.sql`.

### Phase 2 — Native VND end-to-end

- Room price, booking total, admin revenue and API money fields now use integer `long/Long` values.
- Android displays native Vietnamese Dong instead of `$`/USD-like values.
- Removed `VNP_VND_PER_PRICE_UNIT` from backend config and env examples.
- Existing catalog/database prices are migrated once from the legacy demo units to VND.
- Existing Android Room cache is migrated from floating point to integer VND.
- Booking total is a price snapshot. Retrying later does not use the room's newly edited price.
- VNPAY uses the snapshot directly and only applies the protocol scaling: `vnp_Amount = amountVnd * 100`.

Backend migration involved: `V5__money_vnd_bigint.sql`.

Legacy demo prices remain economically equivalent:

```text
50  -> 1,250,000 VND
80  -> 2,000,000 VND
100 -> 2,500,000 VND
120 -> 3,000,000 VND
150 -> 3,750,000 VND
```

Android Room database version is now `4`. `MIGRATION_3_4` converts legacy local money fields to integer VND and also avoids double-converting an already-VND cache.

### Phase 3 — Auth, sync and concurrency

- `TokenAuthenticator` now uses `TokenRefreshCoordinator` + `Mutex` single-flight behavior.
- Concurrent 401 responses wait for the first refresh and reuse the refreshed access token instead of all rotating the refresh token.
- A revoked/invalid refresh token (`401`) clears both the auth session and user booking cache.
- Room catalog sync and booking-history sync are separate WorkManager jobs, so one failure no longer blocks the other.
- Legacy combined WorkManager unique jobs are cancelled after upgrade.
- Added a concurrency unit test for the refresh coordinator.

### Phase 4 — UX, tests and release cleanup

History actions now follow booking state:

```text
PENDING_PAYMENT -> Thanh toán
PROCESSING      -> Kiểm tra thanh toán
FAILED          -> Thử thanh toán lại
SUCCESS         -> no payment action
```

- VNPAY browser deep link now triggers an automatic backend status query.
- Deep-link query parameters never directly decide `SUCCESS` / `FAILED`; backend/IPN state remains authoritative.
- `SUCCESS` does not show a reopen-VNPAY action.
- `PROCESSING` supports status checking even after app restart / History entry.
- Release-critical Compose UI checks use `testTag` instead of brittle display text.
- Important payment/booking strings were moved to `strings.xml`.
- Backend version is `1.0.0`; Android default version name remains `1.0.0`.

## Apply this patch

First make a safety commit of the currently working project:

```powershell
cd D:\BookingHotel2\BookingHotel2
git add .
git commit -m "stable before combined phase 1-4 v1.0.0 patch"
git push
```

Extract the combined ZIP and copy the **contents inside the patch folder** into:

```text
D:\BookingHotel2\BookingHotel2\
```

Choose Merge/Replace. Do not create a nested patch folder inside the project.

## Railway variables

Keep/configure:

```text
VNP_TMN_CODE=B0X8KC9I
VNP_HASH_SECRET=<YOUR REAL SANDBOX HASH SECRET>
VNP_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNP_RETURN_URL=https://bookinghotel-production.up.railway.app/api/payments/vnpay/return
VNP_EXPIRE_MINUTES=15
VNP_CONFIRMATION_GRACE_SECONDS=120
BOOKING_RESERVATION_MINUTES=15
BOOKING_EXPIRATION_SCAN_MS=60000
```

Remove this old variable if it still exists:

```text
VNP_VND_PER_PRICE_UNIT
```

Do not commit or share `VNP_HASH_SECRET`.

VNPAY IPN endpoint remains:

```text
https://bookinghotel-production.up.railway.app/api/payments/vnpay/ipn
```

## One-pass verification on your machine

From the Android project root:

```powershell
cd D:\BookingHotel2\BookingHotel2
.\gradlew testDebugUnitTest
.\gradlew assembleDebug
```

Optional, with emulator/device connected:

```powershell
.\gradlew connectedDebugAndroidTest
```

Then backend:

```powershell
cd D:\BookingHotel2\BookingHotel2\backend
.\gradlew test
.\gradlew build
```

If all are green, run/deploy the backend. Flyway should apply pending migrations in order, including V4 then V5 when they have not yet been applied.

For Railway deployment:

```powershell
cd D:\BookingHotel2\BookingHotel2
git add .
git commit -m "release v1.0.0 booking payment auth sync hardening"
git push
```

After Railway is green, verify:

```text
https://bookinghotel-production.up.railway.app/health
```

Expected health: `UP`.

## Regression checklist

1. Room list/details/admin show VND, e.g. Standard around `1.250.000 ₫`, not `$50`.
2. Create a booking: inventory decreases and booking total is fixed.
3. Leave a booking unpaid until expiry: inventory returns exactly once.
4. CARD/QR simulated failure: booking becomes `FAILED`, inventory returns once, retry can reserve again.
5. Successful CARD/QR: booking becomes `SUCCESS`, inventory remains consumed.
6. VNPAY create: charged amount equals the VND booking total; signed `vnp_Amount` is that amount multiplied by 100.
7. VNPAY return deep link: app opens Payment and queries backend automatically.
8. History: PENDING has Pay, PROCESSING has Check, FAILED has Retry, SUCCESS has no reopen-payment action.
9. Login/session: concurrent API 401s must not generate parallel refresh-token calls; invalid refresh token logs the local session out and clears user booking cache.
10. Background sync: room catalog and booking history run as separate WorkManager jobs.

## Verification performed while preparing this patch

Static checks completed:

- Android XML parsed successfully.
- Money models/API were scanned for remaining `Double` / `BigDecimal` usage.
- Runtime configuration was scanned for the removed conversion variable.
- Main source was checked for leftover USD labels and old price-filter constants.
- Modified source files received a basic structural balance scan.

Full Gradle compilation/tests could not be executed in the preparation environment because Gradle distributions `8.7` and `8.14.4` were not cached and that environment cannot reach `services.gradle.org`. The Gradle wrapper stops before project compilation with `UnknownHostException`, so the commands above on your Windows machine are the final compile/test gate.
