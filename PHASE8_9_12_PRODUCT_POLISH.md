# Phases 8 + 9 + 12 — Product & POS/Fintech Polish

This package builds on the completed Phase 7 project.

## Phase 8 — Realistic booking flow

Added:

- Check-in and check-out date selection with Material 3 DatePicker.
- Guest count.
- Number of nights calculation.
- Total price = price per night × nights × number of rooms.
- Booking confirmation dialog before the REST request.
- Search by room/amenity.
- Filters for availability, price and amenities.
- Price sorting.
- Responsive `LazyVerticalGrid` room list for phone/tablet widths.
- Booking API now carries dates and guest count and the server calculates nights/total.

## Phase 9 — Material 3 / UI polish

Added:

- Consistent custom light/dark Material 3 color schemes.
- Dynamic top app bar titles and back navigation.
- Elevated room, booking and payment cards.
- Loading, empty and error states.
- Snackbar for refresh errors when cached/current room data is still visible.
- Search/filter chips and history status filters.
- Confirmation dialogs for booking and payment.
- Improved typography and responsive room grid.

## Phase 12 — Payment/POS polish

Added:

- Payment states represented as pending / processing / success / failed.
- CARD and QR demo methods.
- Transaction ID and paid timestamp persisted locally.
- Payment receipt in the UI and booking history.
- Idempotency key per payment attempt to protect against duplicate processing.
- Network retry keeps the same idempotency key.
- A business-declined/FAILED retry creates a new payment attempt key.
- Mock server caches results by `bookingId + idempotencyKey`.
- Room database migration from version 1 to 2 preserves existing booking history.

> Payment remains simulated. It does not charge real money or connect to a real payment gateway.

## Build

```powershell
.\gradlew assembleDebug
```

## Unit tests

```powershell
.\gradlew testDebugUnitTest
```

## Instrumentation / Compose tests

Start an emulator, then:

```powershell
.\gradlew connectedDebugAndroidTest
```

Automated tests use fakes/in-memory Room and do not require the Node server.

## Manual integration test

Start the mock API:

```powershell
node mock-server/server.js
```

Then Run the Android app and verify:

1. Search/filter/sort rooms.
2. Choose check-in/check-out dates, guests and room quantity.
3. Verify total changes with nights and room quantity.
4. Confirm booking; verify summary contains dates, guests, nights and total.
5. Pay by CARD or QR and verify a transaction receipt.
6. Verify Room booking history persists payment method, transaction ID and paid time.
7. Create another booking, enable simulated failure, verify FAILED, then retry and verify SUCCESS.
8. During payment, disable the server/network and retry after reconnecting. The client reuses the same idempotency key for an uncertain network result.
9. Existing Phase 5/6 database should migrate from Room schema v1 to v2 without deleting booking history.

## Important architecture after these phases

```text
Compose UI
   ↓
BookingViewModel
   ↓
RoomRepository
   ├── Retrofit → Mock REST API
   └── Room → SQLite booking/payment history
```

The mock REST API intentionally remains a development backend. A later production-style upgrade can replace it with Spring Boot + MySQL without changing the Compose screens' core responsibilities.
