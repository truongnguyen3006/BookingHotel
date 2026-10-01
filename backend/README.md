# Booking Hotel Backend

Spring Boot + MySQL backend for the Booking Hotel Android application.

## Stack

- Java 17
- Spring Boot 4.1.1
- Spring MVC REST API
- Spring Data JPA / Hibernate
- MySQL 8.4
- Flyway migrations
- Bean Validation
- Spring Boot Actuator
- Gradle 8.14.4 wrapper

## Run locally (Windows)

### 1. Start MySQL

From this `backend` directory:

```powershell
docker compose up -d
```

Wait until the container is healthy:

```powershell
docker compose ps
```

Local development credentials:

- host: `localhost`
- port: `3306`
- database: `booking_hotel`
- username: `booking_app`
- password: `booking_app_password`

These credentials are intentionally local-development only. Do not reuse them for a real deployment.

### Alternative: existing MySQL installation

If MySQL is already installed on Windows, you do not need Docker. Run `setup-local-mysql.sql` once as a MySQL administrator, then use the same credentials below.

### 2. Start Spring Boot

```powershell
.\gradlew bootRun
```

The API listens on `http://localhost:8080`.

Health checks:

- `http://localhost:8080/health`
- `http://localhost:8080/actuator/health`

The Android Emulator continues to use `http://10.0.2.2:8080/`, so no Android base URL change is required.

## Main API

```text
GET  /api/rooms
GET  /api/rooms/{id}
POST /api/bookings
GET  /api/bookings/{id}
POST /api/bookings/{id}/payment
```

The JSON contract intentionally matches the existing Retrofit DTOs in the Android app.

## Test

```powershell
.\gradlew test
```

## Inspect MySQL

Use MySQL Workbench or DBeaver and connect to `localhost:3306` with the credentials above. Tables are created by Flyway:

- `rooms`
- `room_amenities`
- `bookings`
- `payments`
- `flyway_schema_history`

## Reset local database

This deletes local MySQL data:

```powershell
docker compose down -v
docker compose up -d
```

## Current scope / deliberate limitations

Phase 13 replaces the in-memory Node mock server with persistent Spring Boot + MySQL storage. Authentication and users are intentionally deferred to Phase 10. Robust concurrent inventory protection is intentionally deferred to Phase 17; this phase uses a database transaction but does not yet add explicit pessimistic/optimistic locking for competing booking requests.
