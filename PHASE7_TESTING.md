# Phase 7 - Unit Test + UI / Instrumentation Test

Phase 7 turns the previous Android Studio sample tests into tests for the actual booking application.

## What is covered

### Local unit tests (`app/src/test`)

- `InMemoryRoomRepositoryTest`
  - valid booking reduces room availability and creates booking history
  - zero / negative / over-stock quantities are rejected
  - simulated payment failure changes booking status to `FAILED`
  - retry changes the same booking to `SUCCESS`

- `RetrofitRoomRepositoryTest`
  - API room DTOs are mapped into repository state
  - booking API result updates inventory and is persisted through `BookingDao`
  - invalid quantity fails before the API is called
  - payment result updates local Room status by `localId`

- `BookingViewModelTest`
  - successful booking updates UI state
  - invalid quantity produces validation error
  - failed payment can be retried successfully
  - `IOException` is mapped to the user-friendly room loading message
  - payment without a booking produces an error immediately

`MainDispatcherRule` replaces `Dispatchers.Main` during JVM tests so `viewModelScope` can be tested without an Android device.

## Device / emulator tests (`app/src/androidTest`)

- `BookingDaoTest`
  - uses a real in-memory Android Room database
  - verifies insert + payment-status update
  - verifies newest booking is returned first

- `BookingFlowUiTest`
  - runs the Compose booking flow with `InMemoryRoomRepository`
  - verifies room -> quantity -> summary -> payment -> booking history
  - verifies a negative quantity shows validation and disables the booking button

The UI test intentionally uses the in-memory repository instead of the Node mock server. This keeps automated UI tests deterministic and independent from a separately running backend process.

## Test tags added to production UI

Small `Modifier.testTag(...)` hooks were added for stable Compose tests:

- `room_card_<id>`
- `history_button`
- `quantity_input`
- `book_button`
- `summary_pay_button`
- `simulate_failure_switch`
- `payment_submit_button`
- `payment_history_button`
- `booking_history_list`

They do not change the visible UI.

## Commands

### 1. Build the app

```powershell
.\gradlew assembleDebug
```

### 2. Run all local JVM unit tests

```powershell
.\gradlew testDebugUnitTest
```

A successful run ends with `BUILD SUCCESSFUL`.

Reports are generated under:

```text
app/build/reports/tests/testDebugUnitTest/index.html
```

### 3. Run Room + Compose UI instrumentation tests

Start an Android emulator first, then run:

```powershell
.\gradlew connectedDebugAndroidTest
```

The Node mock server is **not required** for these automated tests.

Instrumentation reports are generated under:

```text
app/build/reports/androidTests/connected/debug/index.html
```

## Manual regression test

After automated tests pass, the existing real Retrofit flow can still be checked with:

```powershell
node mock-server/server.js
```

Then run the app and verify normal booking/payment/history behavior against the REST API.
