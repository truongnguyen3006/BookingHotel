# Phase 13 - Spring Boot + MySQL Backend

## Goal

Replace the Node.js in-memory mock server with a persistent Java backend while keeping the Android Retrofit contract unchanged.

## Architecture

```text
Android (Kotlin / Compose)
        | Retrofit HTTP
        v
Spring Boot REST API
        | Spring Data JPA / Hibernate
        v
MySQL
```

The Android Emulator still calls `http://10.0.2.2:8080/`. Spring Boot runs on the host machine at port 8080, so the existing Android `API_BASE_URL` does not need to change.

## Backend features

- Persistent rooms, bookings and payment attempts in MySQL.
- Flyway versioned schema migration and initial room seed data.
- Validation and consistent 400/404/409/500 API errors.
- Booking price calculation and inventory update in a Spring transaction.
- CARD/QR payment simulation preserved from the Android project.
- Payment idempotency key stored with a unique database constraint.
- Actuator health endpoint.
- Docker Compose MySQL for local development.

## Start

```powershell
cd backend
docker compose up -d
.\gradlew bootRun
```

Check:

```text
http://localhost:8080/health
http://localhost:8080/api/rooms
```

Then run the Android app normally. **Do not run `node mock-server/server.js` at the same time**, because both use port 8080.

## Test sequence

1. Start MySQL and Spring Boot.
2. Open `/api/rooms`; five seeded room types should be returned.
3. Run Android app; room list should load exactly as before.
4. Create a booking; verify a new row in MySQL `bookings` and reduced `rooms.available_rooms`.
5. Pay with CARD or QR; verify a row in `payments`.
6. Restart Spring Boot; rooms/bookings/payments must remain in MySQL.
7. Restart MySQL container without deleting the volume; data must remain.
8. Android offline cache should still work when backend is stopped (Phase 11).

## MySQL inspection

Connect MySQL Workbench / DBeaver to:

```text
Host: localhost
Port: 3306
Schema: booking_hotel
User: booking_app
Password: booking_app_password
```

Example queries:

```sql
SELECT * FROM rooms;
SELECT * FROM bookings ORDER BY created_at DESC;
SELECT * FROM payments ORDER BY created_at DESC;
```

## What is intentionally NOT Phase 13

- User/login/JWT -> Phase 10.
- Full booking concurrency/locking -> Phase 17.
- Production secrets, staging/release environments -> Phase 20.
- Deployment pipeline -> Phase 21.
