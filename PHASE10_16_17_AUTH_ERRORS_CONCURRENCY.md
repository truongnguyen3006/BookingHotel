# Phase 10 + Phase 16 + Phase 17

This package builds on Phase 13 (Spring Boot + MySQL).

## Phase 10 - Authentication + User

Backend:
- Spring Security, stateless JWT access tokens
- BCrypt password hashing
- Refresh-token rotation; only SHA-256 hashes of refresh tokens are stored in MySQL
- `USER` / `ADMIN` roles
- User ownership on bookings and payments
- `GET /api/bookings` returns only the authenticated user's bookings

Android:
- Login / Register / Profile / Logout
- Access + refresh token session persisted with Jetpack DataStore
- Bearer token added by an OkHttp interceptor
- OkHttp Authenticator refreshes an expired access token and retries one request
- Local booking history is cleared when accounts change, then re-synced from the authenticated user's backend history

### Auth endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

`GET /api/rooms/**` remains public. Booking/payment endpoints require authentication.

## Phase 16 - Production-style API errors

Backend error body now has a stable shape:

```json
{
  "code": "ROOM_UNAVAILABLE",
  "message": "Not enough rooms available",
  "path": "/api/bookings",
  "timestamp": "2026-09-30T09:00:00Z",
  "details": {}
}
```

Examples:
- `400 VALIDATION_ERROR`
- `401 AUTH_REQUIRED`
- `401 INVALID_CREDENTIALS`
- `403 FORBIDDEN`
- `404 BOOKING_NOT_FOUND`
- `409 ROOM_UNAVAILABLE`
- `409 ROOM_BUSY`
- `409 BOOKING_ALREADY_PAID`
- `500 INTERNAL_ERROR`

Android parses the JSON error code centrally and maps it to a Vietnamese user-facing message.

## Phase 17 - Booking concurrency / overselling protection

`RoomJpaRepository.findByIdForUpdate()` uses a JPA `PESSIMISTIC_WRITE` lock.

Within `BookingService.createBooking()`:
1. A transaction starts.
2. The selected `rooms` row is locked.
3. Inventory is checked while the lock is held.
4. Inventory is decreased and the booking is persisted.
5. The transaction commits and releases the lock.

If only one room remains, two simultaneous requests cannot both reserve it. The second request waits, sees the new inventory, and returns `409 ROOM_UNAVAILABLE`.

## Database migration

Flyway automatically applies `V2__authentication_and_booking_owner.sql` to the Phase 13 database.

It creates:
- `users`
- `refresh_tokens`
- `bookings.user_id`

Existing Phase 13 bookings are preserved with `user_id = NULL`; new bookings are owned by an authenticated user.

## Local development security

`application.yml` includes a development-only JWT secret fallback. For any staging/release environment set:

```text
JWT_SECRET_B64=<base64 encoded secret of at least 32 bytes>
JWT_ACCESS_MINUTES=30
JWT_REFRESH_DAYS=14
```

Do not commit a real production secret.

## Run

Keep MySQL running:

```powershell
cd D:\BookingHotel2\BookingHotel2\backend
docker compose up -d
docker compose ps
```

Then run Spring Boot:

```powershell
.\gradlew bootRun
```

The Android emulator continues to use:

```text
http://10.0.2.2:8080/
```

Do not run the old Node mock server on port 8080.

## Recommended tests

### Backend

```powershell
cd backend
.\gradlew test
```

### Android

```powershell
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
.\gradlew connectedDebugAndroidTest
```

### Manual auth flow

1. Start MySQL and Spring Boot.
2. Run Android.
3. Register a new account.
4. Close and reopen the app: the session should restore from DataStore.
5. Create a booking and payment.
6. Open Profile and logout.
7. Register/login with another account: it must not receive the first account's server bookings.
8. Login with the first account again: booking history is reloaded from the backend.

### Manual concurrency test

1. Register/login and copy an access token (or use the Android flow).
2. In MySQL Workbench prepare one room with exactly one available unit:

```sql
UPDATE rooms SET available_rooms = 1 WHERE id = 1;
```

3. Send two authenticated `POST /api/bookings` requests at almost the same time for room 1, quantity 1.
4. Expected result: exactly one request succeeds; the other returns HTTP 409 with `ROOM_UNAVAILABLE` (or `ROOM_BUSY` if a lock timeout occurs).

## Notes / intentionally deferred

- Real email verification / forgot-password
- OAuth / Google login
- Refresh-token cleanup job
- Admin screens
- Rate limiting
- WorkManager background synchronization
- CI/CD, monitoring and release configuration
