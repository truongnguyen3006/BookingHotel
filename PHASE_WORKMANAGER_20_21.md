
## Baseline fixes carried forward

This package includes the fixes validated while completing Phases 10/16/17:

- Spring Boot 4 security JSON writer uses Jackson 3 `JsonMapper` (`tools.jackson...`) instead of Jackson 2 `com.fasterxml.jackson...`.
- `TokenAuthenticator` uses the OkHttp Java accessors `response.request()` / `priorResponse()` required by the resolved OkHttp API.
- `AuthScreen` avoids the delegated-property smart-cast compiler error by copying the UI state to a local value before casting.
- `concurrency-test.ps1` keeps the date values as `DateTimeOffset`, stops on setup errors, and returns a failing exit code unless the result is exactly one `201` and one `409`.

# WorkManager + Phase 20 + Phase 21

This package continues from the Phase 10 + 16 + 17 baseline.

## 1. WorkManager background synchronization

### What it does

The app now schedules a unique periodic `BackgroundSyncWorker` every 6 hours with a `CONNECTED` network constraint.

The worker:

1. Refreshes the room inventory from the Spring Boot API.
2. Writes fresh room data into the Room cache.
3. If a user session exists, refreshes that user's booking history.
4. Retries transient network / HTTP 5xx failures with exponential backoff.
5. Does **not** queue booking or payment mutations. Booking/payment still require live server confirmation to avoid duplicate or stale transactions.

### Main files

- `background/BackgroundSyncWorker.kt`
- `background/BackgroundSyncScheduler.kt`
- `BookingHotelApplication.kt`
- `AndroidManifest.xml`

### Test

1. Run Spring Boot + MySQL.
2. Open the app once so periodic work is registered.
3. Android Studio -> App Inspection -> Background Task Inspector / WorkManager, or use Logcat, to inspect scheduled work.
4. You can temporarily call `BackgroundSyncScheduler.enqueueImmediateSync(context)` from a debug-only action if you want to force a worker during development.
5. Stop the backend and verify the app can still browse the Room cache; booking/payment must still fail while offline.

---

## 2. Phase 20 - Environment configuration and secrets

### Android build environments

The app now has three build types:

- `debug` -> local development (`http://10.0.2.2:8080/` by default)
- `staging` -> staging API
- `release` -> production API

`BuildConfig.API_BASE_URL` and `BuildConfig.ENVIRONMENT` are generated per build type.

The staging and production defaults intentionally use `.invalid` domains so a release build does not silently connect to localhost.

Override URLs by either:

### Option A - User Gradle properties

Create/update:

Windows:

```text
%USERPROFILE%\.gradle\gradle.properties
```

Add:

```properties
BOOKING_API_LOCAL_URL=http://10.0.2.2:8080/
BOOKING_API_STAGING_URL=https://staging.your-domain.com/
BOOKING_API_PROD_URL=https://api.your-domain.com/
```

### Option B - Environment variables

Use the same names:

```text
BOOKING_API_LOCAL_URL
BOOKING_API_STAGING_URL
BOOKING_API_PROD_URL
```

Do not place backend DB passwords or JWT secrets inside the Android app. Anything shipped in an APK can be extracted.

### Backend profiles

Spring Boot now uses:

```text
local
staging
prod
```

Default is `local`.

- `application-local.yml` has safe local-development defaults.
- `application-staging.yml` requires DB/JWT values from environment variables.
- `application-prod.yml` requires DB/JWT values from environment variables.

Required staging/prod secrets:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET_B64
```

Optional:

```text
DB_POOL_MAX
DB_POOL_MIN
JWT_ACCESS_MINUTES
JWT_REFRESH_DAYS
SERVER_PORT
```

Use `backend/.env.example` as a template. Never commit `backend/.env`.

### Local Docker

MySQL only (same behavior as before):

```powershell
cd backend
docker compose up -d
.\gradlew bootRun
```

Full local stack in Docker:

```powershell
cd backend
docker compose --profile full up -d --build
```

---

## 3. Phase 21 - CI/CD

Two workflows are included.

### `.github/workflows/android-ci.yml`

On push / pull request to `main`:

- JDK 17
- Android SDK setup
- Gradle cache/setup
- `lintDebug`
- `testDebugUnitTest`
- `assembleDebug`
- `assembleStaging`
- upload APK artifacts
- run Compose / instrumentation tests on an Android emulator

### `.github/workflows/backend-ci-cd.yml`

On push / pull request to `main`:

- JDK 17
- backend unit tests
- Spring Boot JAR build
- upload JAR artifact
- Docker image build

On a push to `main`, the workflow additionally publishes the backend image to GitHub Container Registry (GHCR):

```text
ghcr.io/<github-user-or-org>/<repository>-backend:latest
```

and a commit-SHA tag.

The workflow uses the repository `GITHUB_TOKEN`; no Docker Hub password is required.

### First GitHub run

After pushing these files:

1. Open GitHub -> repository -> Actions.
2. Confirm `Android CI` and `Backend CI/CD` run.
3. If GHCR publishing is blocked, repository/org package permissions may need to allow GitHub Actions package write access.
4. Open a successful workflow run and download the APK/JAR artifacts to verify delivery output.

---

## Recommended verification order

```powershell
# Android
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
# emulator must be running for this one
.\gradlew connectedDebugAndroidTest

# staging variant compile check
.\gradlew assembleStaging

# Backend
cd backend
.\gradlew test
.\gradlew bootJar

docker build -t booking-hotel-backend:local .
```

Then manually test:

1. Login/register.
2. Load rooms.
3. Book a room.
4. Pay successfully.
5. Verify user-scoped booking history.
6. Stop backend: cached room browsing still works.
7. Restart backend and refresh.
8. Verify the periodic worker appears in WorkManager inspection.

## What to say in an interview

> The Android client uses WorkManager for reliable, network-constrained periodic synchronization of server data into a Room cache. Environment-specific API URLs are generated per build type and backend secrets are supplied through environment variables rather than committed source. GitHub Actions runs Android/backend tests and builds artifacts on each change, and publishes the backend Docker image to GHCR on main.
