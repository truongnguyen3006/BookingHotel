# Phase 11 - Offline-first room cache

## Goal

The room list now uses a local Room cache as a fallback instead of depending entirely on the REST API.

The data flow is:

```text
Compose UI
   ↓
BookingViewModel
   ↓
RoomRepository
   ├── Retrofit / REST API       (remote source)
   └── Android Room / SQLite     (local cache)
```

## Behavior

`RetrofitRoomRepository.refreshRooms()` follows a cache-first, network-refresh strategy:

1. Read `room_cache` from Room.
2. If cached rooms exist, publish them immediately.
3. Request fresh rooms from the REST API.
4. If the request succeeds, replace the Room cache and publish network data.
5. If the request fails but cached rooms exist, keep the cached data and continue without crashing.
6. If both the network and cache are unavailable, surface the network error to the UI.

The UI shows an offline-cache chip while cached room data is being used. Tapping it retries synchronization.

Booking/payment writes are still network-only. This is intentional: a client must not create authoritative bookings locally while the server cannot validate inventory/payment state.

## Database changes

`BookingDatabase` is upgraded from version 2 to version 3.

New table:

```text
room_cache
- id (PK)
- imageKey
- typeKey
- pricePerNight
- amenities
- availableRooms
- lastUpdatedAt
```

`MIGRATION_2_3` creates this table without deleting existing booking history.

The Room cache stores stable keys (`typeKey`, `imageKey`) rather than Android resource IDs. Resource IDs are reconstructed in the mapper when cached data is read.

## Important production rule

Cached room inventory is useful for browsing, but it is not authoritative for creating a booking.

Even when a cached room says `availableRooms = 1`, the booking request still goes to the server. The server must validate the latest inventory and may reject the request with `409 Conflict`.

## Automated tests added/updated

- Retrofit repository: network response is cached.
- Retrofit repository: network failure falls back to cached rooms.
- Retrofit repository: network + empty cache returns failure.
- Booking response updates cached room inventory.
- `RoomCacheDaoTest`: replace-all behavior, amenities conversion and upsert inventory behavior.

The AndroidX Test versions in the project are also kept at the newer versions used to support the Android 17 emulator:

- AndroidX JUnit 1.3.0
- Espresso 3.7.0
- Test Runner 1.7.0

## Local validation

Do not uninstall the existing app before the first Phase 11 run. Keeping the app installed tests the real Room database migration from v2 to v3.

### Build / automated tests

```powershell
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
.\gradlew connectedDebugAndroidTest
```

### Manual offline-first test

1. Start the mock API:

```powershell
node mock-server/server.js
```

2. Run the app and verify the room list loads.
3. Close the app completely.
4. Stop the mock API with `Ctrl + C`.
5. Reopen the app.
6. The room list should still appear from Room cache.
7. The UI should display `Đang dùng dữ liệu đã lưu`.
8. Search/filter/sort should continue working on cached data.
9. Creating a new booking should still fail while the server is offline (expected behavior).
10. Restart the mock API.
11. Tap the offline-cache chip to synchronize again.
12. The cache indicator should disappear after a successful network refresh.

If the app crashes on startup with a Room schema/integrity error, do not uninstall it. Capture the error so the v2 -> v3 migration can be fixed rather than hidden by deleting the database.
