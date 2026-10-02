# Phase 5 — Final Audit Fixes

Base: `main` at `9d7f0052139eb827540a7322008ab0504ddeaf68` (`trigger railway deploy v1.0.0`).
Work branch: `phase5-final-audit-fixes`. No merge to main and no production deployment were performed.

This change closes the identified code paths and adds regression coverage. It does **not** certify v1.0.0 as production-ready. Real MySQL concurrency, Android device behavior, merchant callbacks, existing database reconciliation, and deployment separation remain release gates until their results are recorded.

## Findings and fixes

| Requirement / problem | Implemented correction | Important files / coverage |
|---|---|---|
| 1. CARD/QR could bypass a pending provider attempt; keys could cross payment methods | Lock the owned booking before checking attempts; reject any active PENDING attempt and previously successful payment; validate the method before returning an idempotent result. Disable client-triggered demo payment endpoints in production and hide those methods in release Android. | `PaymentService`, `PaymentServiceLifecycleTest`, `PaymentInventoryIT`, `PaymentScreen`, build/profile configuration |
| 1, 10. IPN caught exceptions inside the transaction, risking a partial commit | Let exceptions leave the transactional service proxy. Catch only in the controller, after rollback, and reply with provider code 99. Lifecycle operations require an existing transaction. | `VnPayService`, `VnPayController`, `BookingLifecycleService`; rollback unit and real-database tests |
| 1, 10, 11. Concurrent IPNs could use a stale managed Payment; latest-attempt MAX subqueries could read an older MySQL snapshot | Discover only the scalar booking ID, then lock booking followed by payment. All payment/expiry paths use the same lock ordering. Idempotency lookup is a locking read; latest attempt uses a single native ORDER BY/LIMIT/FOR UPDATE current read instead of a snapshot-prone MAX subquery. Terminal attempts reject later callbacks, and released reservations cannot be resurrected. Retire an orphan PENDING callback as FAILED/SUPERSEDED without changing inventory. | `PaymentJpaRepository`, `VnPayService`, replay/race tests |
| 1. Existing duplicate/orphan PENDING rows lacked a database exclusivity constraint | New V6 migration closes attempts on terminal/released bookings, retains only the latest overlapping PENDING attempt, and adds a unique generated nullable key for active booking ID. Successful financial records are not rewritten. | `V6__exclusive_active_payment.sql`, `FlywayMigrationIT` |
| 2. A response started in session A could write private rows after logout/login B, including a new login by the same user | Give each login a distinct session UUID; preserve it through token rotation. Capture the session before authenticated work, check it before work and under the same mutex as login/logout at each private write. Filter observable cache rows by session owner. Clear private cache atomically relative to writes on login/logout. | `AuthSession`, `SessionStore`, `TokenStore`, `RetrofitRoomRepository`, repository race tests |
| 2. Worker/UI refreshes could replace rows while a payment held their local IDs | Serialize private history/booking/payment operations and atomically replace owned history while preserving existing local IDs and receipts. Cancel authenticated workers on logout and schedule them after login/restore. | `BookingDao`, `BackgroundSyncScheduler`, Room/repository tests |
| 2. Old Room rows had no provable owner | Room version 5 adds `ownerSessionId`; migration 4→5 discards only unowned private booking cache and refetches it. Public room cache and existing VND migrations remain intact. | `BookingDatabase`, `BookingEntity`, `DatabaseModule` |
| 3. FAILED/retry/status changes left catalog/details stock stale, and an older GET/cache read could overwrite newer state | Serialize catalog requests with booking/payment mutations. Refresh from the network after all payment outcomes, VNPAY create/retry/status, and history sync. Publish cache only while memory is empty; mark offline retained content as CACHE. Keep the selected detail room synchronized with the catalog. Foreground resume and 30-second foreground history polling catch scheduler/IPN updates. | `RetrofitRoomRepository`, `BookingViewModel`, `BookingApp`, inventory and deferred-GET tests |
| 4. Staging reused the production Railway URL and default remote builds appeared configured | Remove the committed staging URL. Debug retains emulator-local API. Staging/release use separate BuildConfig properties, non-routable `.invalid` sentinels, and variant build validation. Staging requires HTTPS and rejects a URL equal to the configured production URL. Use distinct staging DB/JWT/merchant variable names and force the Sandbox pay URL for the staging profile. | `app/build.gradle.kts`, `gradle.properties`, Android/backend environment examples, `application-staging.yml`, CI |
| 5, 17. Fragile selectors and missing UI regression coverage | Important flows use test tags, including confirmation loading/error, auth fields/actions, history status and payment actions/results. Booking status has a semantic property so tests assert domain SUCCESS independently of displayed wording. | `BookingSemantics`, `BookingFlowUiTest`, `AuthFlowUiTest`, screens |
| 6, 17. Important Compose/ViewModel/error copy remained embedded in code | Centralize the current copy in `strings.xml`, including authentication, rooms, dates/dialogs, payment, history, profile, admin, loading/empty/error/offline messages and filter labels. Inject an `AppStrings` resolver for non-Compose UI logic. No language was added. | `strings.xml`, `AppStrings`, `ApiErrorMapper`, ViewModels/screens |
| 7. Confirmation disappeared before loading; rapid taps could queue requests | Keep the dialog open while booking is loading, disable confirmation/dismissal and inputs, show a loading indicator/error, and close only on success. Set ViewModel loading synchronously before launching the request and guard repeated booking/payment/status submissions. | `RoomDetailsScreen`, `BookingViewModel`, deferred-request unit/UI tests |
| 8. Backend wrapper was not executable in Git | Track both wrappers as mode `100755`; retain CI chmod fallback. | `backend/gradlew`, existing root `gradlew`, CI |
| 9. Refresh/logout races could restore a revoked session or retry an A request with B's token | Tag authenticated requests with their session identity. Share the refresh coordinator between restore and Authenticator. Compare-and-save refreshed credentials, compare-and-clear invalid refresh responses, preserve the login session ID, and clear logout locally before server revoke. Explicit logout still clears the same session if its token rotated meanwhile. | `TokenAuthenticator`, `AuthInterceptor`, `RetrofitAuthRepository`, `TokenStore`, 3-request single-flight and session-race tests |
| 10, 11. Concurrency/idempotency behavior lacked real database coverage | Add MySQL Testcontainers tests for duplicate SUCCESS/FAILED, late callbacks, rollback, CARD/QR versus VNPAY, active retry, two-device creation, same-key concurrent demo receipts, CARD-versus-VNPAY race, one-room/two-user booking, expiry versus success, retry versus expiry, retry versus another user's booking and concurrent duplicate IPNs. | `PaymentInventoryIT`; mandatory `integrationTest` build/CI gate |
| 12. V4/V5 applied-data behavior was not regression-tested | Start at V3 with existing users/bookings/payments and leaked failed inventory, migrate through V4 and V5, check 50→1,250,000 / 80→2,000,000 / 120→3,000,000 VND, preserve existing payment amounts, then rerun and assert no second migration. Also verify V6 cleanup and unique-index rejection. V1–V5 were not changed. | `FlywayMigrationIT` |
| 13. Untrusted/foreign/stale deep links could select stale private data | Parse only the expected scheme/host and one positive integer booking ID; reject missing/malformed/duplicate/overflow IDs. Fetch fresh owned history before resolving the ID. Ignore URI responseCode for payment state. Already SUCCESS/FAILED bookings render their backend history state without querying a different provider attempt. Logged-out links wait for login; startup intent handling supports process recreation; repeated links fetch again safely. | `PaymentReturn`, `BookingApp`, `BookingViewModel`, parser and resolution tests |
| 14. History errors were silent; missing room/admin empty states and unbounded calls gave poor recovery | Add history loading/error/retry, snackbar feedback for link/history failures, missing-room recovery, admin empty copy, browser-launch error handling and a 30-second HTTP call timeout. Existing offline catalog fallback remains visible. | screens, ViewModels, `NetworkModule` |
| 15, 17. Old money/config guidance and simulated-payment copy were misleading | Mark the old audit as historical, update Sandbox/deployment/config guidance, remove production defaults from staging examples, retain the old 25,000 factor only in one-time migration/history explanations. Runtime uses native VND and `vnp_Amount = amountVnd * 100`. Leave compatibility worker names/classes used for upgrade cleanup. | `AUDIT_REPORT.md`, `VNPAY_SANDBOX.md`, `DEPLOY_RAILWAY.md`, environment examples |
| 16. Security needed regression coverage after lifecycle changes | Test foreign booking read/retry/status/payment access, USER→admin denial, unauthenticated access, arbitrary client SUCCESS method, server-calculated price, and invalid checksum/amount/merchant. Escape provider-return message before rendering HTML. Production demo endpoints reject simulated success. | `PaymentInventoryIT`, `PaymentServiceLifecycleTest`, `VnPayController` |

## Tests and execution evidence

The temporary execution environment initially had no JDK compiler or Android SDK. A JDK 17, the repository's wrapper versions and Android SDK 35 were provisioned outside the repository. Mockito is explicitly attached as a test Java agent because self-attachment is unavailable here. No toolchain files or credentials are committed.

| Command (from repository root unless noted) | Result |
|---|---|
| `./gradlew testDebugUnitTest` | 37 unit tests passed; 0 failures/errors/skips. |
| `./gradlew assembleDebug` | Passed; debug APK assembled. |
| `./gradlew assembleDebugAndroidTest` | Passed; instrumentation sources compiled and test APK assembled. |
| `./gradlew lintDebug` | Passed with 0 errors, 20 warnings and 2 informational findings. Warnings include inherited target-API/KAPT/resource/photo-density recommendations and copy typography; this is not a zero-warning claim. |
| `./gradlew connectedDebugAndroidTest` | Not executed: no attached device/emulator (`adb devices -l` returned an empty list; `/dev/kvm` is absent). The task was started and interrupted while waiting for ADB/device discovery; no instrumentation assertions ran. |
| `cd backend && ./gradlew test` | 23 unit tests passed, 0 failures/errors. |
| `cd backend && ./gradlew compileTestJava bootJar` | Passed; all MySQL integration test sources compiled and Spring Boot JAR was produced. |
| `cd backend && ./gradlew build --continue` | Build gate blocked: both Testcontainers classes failed initialization because no Docker daemon/socket is available. Integration test methods did not execute locally; these are not business assertion failures. JAR assembly succeeded. |
| Android staging/release configuration guard | Both validation tasks rejected unconfigured `.invalid` defaults as intended. No real staging/release URL was supplied, so no remote variant was built against an invented endpoint. |
| `git diff --check`; resource-reference/XML checks; applied migration diff | Passed. No edits to backend V1–V5. No literal-text selectors remain in important instrumentation flows. |

New/strengthened Android unit coverage includes 3 simultaneous 401s→exactly 1 refresh→all requests use the new access token; 401/403 refresh rejection; temporary network failure; logout/session switch while refresh is pending; old-session retries; A→B and same-user/new-login stale history; stale catalog GET versus booking; inventory after failure/retry/status; rapid double submit; malformed/untrusted deep link IDs; fresh-owned/foreign/offline/repeated terminal link resolution.

Instrumentation coverage includes booking validation/success/history semantic status, demo failure/retry, dialog loading/disabled confirmation, auth validation/server error/toggle, and atomic owned Room history replacement with stable receipt/local ID. Compiling these tests is distinct from executing them. GitHub Actions run [37003243937](https://github.com/truongnguyen3006/BookingHotel/actions/runs/37003243937) subsequently passed Android lint/unit/debug build and all 11 instrumentation tests on an API 35 emulator; staging build was skipped because no staging URL is configured.

The first real MySQL CI run [37003243911](https://github.com/truongnguyen3006/BookingHotel/actions/runs/37003243911) executed 18 integration tests: 12 passed and 6 failed. One failure exposed the REPEATABLE READ latest-attempt query race, now corrected with a single locking read and locking idempotency lookup. Four failures came from stubbing a MANDATORY transactional spy through its proxy (including cascading unfinished stubs); tests now stub/reset the unwrapped target while calls under test still use real transactional service proxies. The anonymous assertion now explicitly supplies an anonymous test principal. Two further concurrent-method/idempotency tests were added (20 integration methods total). Backend unit tests and all test sources were recompiled successfully after these corrections; updated MySQL CI execution is required.

Backend CI now runs `test integrationTest build` on a Docker-capable runner. Android CI runs lint/unit/debug build, configured staging build, and emulator instrumentation. A PR build does not publish a production container or deploy a backend. CI results must be checked separately; a newly created PR alone is not evidence of passing CI.

## Required configuration

No real staging URL or merchant credentials were supplied or invented. Create a separate staging backend **and** MySQL service before running Sandbox regression.

| Environment | Required settings |
|---|---|
| Android debug | `BOOKING_API_LOCAL_URL` (default `http://10.0.2.2:8080/`; actual LAN URL for a physical device). |
| Android staging | `BOOKING_API_STAGING_URL=<actual staging HTTPS base URL>/`; set `BOOKING_API_PROD_URL` too so equality can be rejected. Set corresponding GitHub Actions repository variables for optional staging CI. |
| Android release | `BOOKING_API_PROD_URL=<actual production HTTPS base URL>/`; existing release signing variables/keystore properties. CARD/QR demos are hidden. |
| Backend staging | `SPRING_PROFILES_ACTIVE=staging`, `STAGING_DB_URL`, `STAGING_DB_USERNAME`, `STAGING_DB_PASSWORD`, `STAGING_JWT_SECRET_B64`, `STAGING_VNP_TMN_CODE`, `STAGING_VNP_HASH_SECRET`, `STAGING_VNP_RETURN_URL=<actual staging HTTPS origin>/api/payments/vnpay/return`. Use Sandbox merchant credentials. |
| Staging test controls | `STAGING_DEMO_PAYMENTS_ENABLED=true` only for controlled CARD/QR regression; use `false` for provider-only regression. `BOOKING_RESERVATION_MINUTES=15`, `VNP_EXPIRE_MINUTES=15`, `VNP_CONFIRMATION_GRACE_SECONDS=120`; scheduler interval default 60 seconds. |
| Backend production (future manual release) | `SPRING_PROFILES_ACTIVE=prod`, production `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, distinct `JWT_SECRET_B64`, approved production `VNP_TMN_CODE`, `VNP_HASH_SECRET`, `VNP_PAY_URL`, `VNP_RETURN_URL`. Production demo payments are false. |

Configure the merchant's staging IPN URL as `<actual staging HTTPS origin>/api/payments/vnpay/ipn`, separately from Return URL. Verify externally reachable HTTPS, forwarded IP behavior, Sandbox merchant registration and credentials. Remove any obsolete `VNP_VND_PER_PRICE_UNIT` variable from Railway. Never share JWT/DB/merchant secrets between environments.

URL validation cannot know which server/database a developer labels "staging"; check actual Railway service and database identity before payment tests. V6 cleans provider records locally; it cannot cancel or refund a charge at the payment provider.

## Exact manual staging regression

Record build commit, device/API, backend profile, DB identity, merchant environment, starting stock, booking/payment IDs, txnRefs, response/status codes and final SQL states for each scenario. Use fresh test users A/B and controlled staging data.

1. **Run mandatory automated gates with Docker and an emulator/device.** From a clean checkout of this branch, with JDK 17 and SDK 35:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
   ./gradlew connectedDebugAndroidTest
   cd backend
   ./gradlew test integrationTest build
   cd ..
   ```

   On Windows use `gradlew.bat` / `backend\gradlew.bat` for the same tasks. Ensure Docker can start `mysql:8.4`; do not replace the concurrency suite with H2 or exclude it to claim a passing build.

2. **Provision and configure staging manually.** Fill `backend/deploy/staging.env.example` in the staging service's environment, confirm its DB is not production, register Sandbox IPN/Return URLs, start the staging backend, verify `/actuator/health` and `/api/rooms`. Check Flyway history contains successful versions 1–6. Reboot and confirm no migration reruns or price changes. Deployment is a separate operator action; this branch does not deploy it.

3. **Build/install the actual staging app.** Set the actual URLs locally (or use `-P` arguments):

   ```bash
   ./gradlew assembleStaging -PBOOKING_API_STAGING_URL="$BOOKING_API_STAGING_URL" -PBOOKING_API_PROD_URL="$BOOKING_API_PROD_URL"
   adb install -r app/build/outputs/apk/staging/app-staging.apk
   ```

   Confirm `BuildConfig.ENVIRONMENT=staging`, correct HTTPS API URL, package `com.example.bookinghotel.staging`, separate merchant/DB and no production traffic.

4. **Booking/double submit.** Set test room stock to 5 with no existing reservations. Login A, book quantity 1 for valid future dates. Tap confirm repeatedly during a deliberately slow connection: dialog remains open/disabled, exactly one POST is sent, one booking exists and stock becomes 4. Validate zero/negative/excess quantity, invalid guests/dates, and server timeout/error with a usable retry.

5. **Demo failure/retry inventory.** With staging demo flag enabled, make CARD/QR fail: booking/payment FAILED, `inventory_released=1`, catalog and details automatically show 5. Retry VNPAY: booking PROCESSING, one PENDING attempt, `inventory_released=0`, UI/backend stock 4 without restart/manual pull. Complete Sandbox successfully and verify stock stays 4. A successful booking must have a successful payment.

6. **Payment exclusivity/replay.** While VNPAY is active, POST CARD and QR to `/api/bookings/{id}/payment` with new keys; expect conflict and no new payment/stock change. Create VNPAY from two devices with different keys; both reuse one txnRef. Save a genuine signed Sandbox IPN request in a protected local test fixture and replay the identical request twice: first code 00, subsequent code 02, no duplicate release/consume. Do not edit signed query fields without re-signing with the staging secret.

7. **Failure/late success/expiry.** Cancel/decline a Sandbox attempt and replay its failure; inventory restores exactly once. Deliver a saved signed success after a failed or scheduler-expired attempt: code 02, no resurrection. Deliver a failure after SUCCESS: code 02, SUCCESS remains. For expiry testing only, temporarily use short reservation/payment periods in the staging service, wait through URL expiry + confirmation grace + scheduler interval, then verify FAILED/no PENDING and foreground UI stock update within the next history poll. Restore the normal timing configuration afterward. Any provider charge after release requires operator reconciliation/refund.

8. **Database races.** Execute `PaymentInventoryIT` against its disposable MySQL via `./gradlew integrationTest --tests '*PaymentInventoryIT'`; inspect one-room/two-user, retry/new booking, retry/scheduler, IPN/scheduler and duplicate-IPN cases. Repeat the suite under CI/load as needed; deterministic unit mocks alone cannot prove row-lock behavior. Verify rollback injection leaves Payment PENDING + Booking PROCESSING + reserved inventory together.

9. **Account/refresh/cache.** Delay A's `/api/bookings` response via a test proxy; logout A, login B, then release A's response. B sees only B's bookings and Room rows; A's response is discarded. Repeat logout→login A with a new session, UI versus WorkManager refresh, and app restart. Trigger 3 concurrent expired-access requests: capture exactly one refresh call and three retries with the rotated access token. Exercise refresh 401/403, temporary offline refresh, logout and B login during refresh; no old token/session/cache returns. Verify authenticated workers cancel/reschedule.

10. **Deep links.** Substitute IDs from A/B bookings in these staging-device commands:

    ```bash
    adb shell am start -a android.intent.action.VIEW -d 'bookinghotel://payment-result?bookingId=1&responseCode=00' com.example.bookinghotel.staging
    adb shell am start -a android.intent.action.VIEW -d 'bookinghotel://payment-result?bookingId=abc' com.example.bookinghotel.staging
    adb shell am start -a android.intent.action.VIEW -d 'bookinghotel://payment-result?responseCode=00' com.example.bookinghotel.staging
    adb shell am force-stop com.example.bookinghotel.staging
    ```

    After force-stop, reopen using a valid deep link. Also test missing/negative/overflow/duplicate ID, B's booking while logged in as A, logged out then login, repeated URI, already SUCCESS, already FAILED and offline. A forged `responseCode=00` must never create SUCCESS. Offline/foreign results show an error; reconnect and retry owned history/payment status. Confirm no browser handler produces a recoverable message.

11. **Security and UX.** Use A's bearer token to read/retry/check B's booking: deny without data/stock changes. USER admin requests return 403 and unsigned private requests return 401. Supply a bogus price or method SUCCESS: server price remains authoritative/arbitrary payment state is rejected. Send invalid checksum, signed wrong amount and signed wrong merchant callbacks: no mutation. Exercise empty rooms/history/admin, API 500, offline, timeout and expired-session screens; check copy/tags/loading/retry and no indefinite spinner. Manually validate Room v4→v5 upgrade retains public VND prices and clears/refetches private history.

12. **Check SQL invariants and record evidence.** After each lifecycle/race scenario run the following read-only queries on staging; all should return zero rows. Reconcile any pre-existing exceptions before promotion:

    ```sql
    SELECT booking_id, COUNT(*) FROM payments WHERE status='PENDING'
    GROUP BY booking_id HAVING COUNT(*) > 1;

    SELECT b.id, b.status, b.inventory_released, p.id, p.status
    FROM bookings b JOIN payments p ON p.booking_id=b.id
    WHERE p.status='PENDING'
      AND (b.status IN ('SUCCESS','FAILED') OR b.inventory_released=TRUE);

    SELECT b.id FROM bookings b
    WHERE b.status='SUCCESS' AND (
      b.inventory_released=TRUE OR NOT EXISTS (
        SELECT 1 FROM payments p WHERE p.booking_id=b.id AND p.status='SUCCESS'));

    SELECT b.id, b.status, b.inventory_released, p.id
    FROM bookings b JOIN payments p ON p.booking_id=b.id
    WHERE p.status='SUCCESS' AND (b.status<>'SUCCESS' OR b.inventory_released=TRUE);

    SELECT id, available_rooms FROM rooms WHERE available_rooms < 0;
    ```

## Remaining limitations / release gates

- Local Testcontainers execution requires Docker; device/Compose execution requires an emulator or physical Android device. Real merchant payment, IPN reachability, staging infrastructure and secrets cannot be validated without those supplied environments.
- Existing SUCCESS-payment / FAILED-released-booking corruption and SUCCESS bookings without a successful payment need financial/inventory reconciliation. V6 intentionally does not manufacture successful payments, resurrect reservations, rewrite successful receipts or perform refunds. Take a backup and inspect the SQL above before applying migrations to a deployed database.
- A late provider charge after reservation release is rejected locally and needs reconciliation/refund outside this application. There is no automated provider inquiry/refund workflow here.
- Inventory remains the existing aggregate room-count model, rather than a date-indexed hotel occupancy ledger. Offline inventory remains cached and labeled as such; remote server changes become visible on successful foreground refresh/poll or worker sync.
- CARD/QR remain simulated methods only in local/controlled staging. They are not implemented real payment rails and cannot certify production payment readiness.
- Staging/release URL guards validate syntax and obvious equality only; an operator must verify the actual backend profile/database/merchant and release signing/installation before promotion.
- Full process recreation, real browser Return/IPN ordering, device background scheduling, upgrade compatibility and real Sandbox outcomes require the manual steps above. No production deployment or automatic merge is included.
