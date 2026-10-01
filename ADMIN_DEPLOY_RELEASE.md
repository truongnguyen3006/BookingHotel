# Admin Module + Deployment + Phase 22 Release

## What was added

### Admin Module

The existing `USER` / `ADMIN` role is now enforced end-to-end.

Backend endpoints (ADMIN only):

```text
GET   /api/admin/dashboard
GET   /api/admin/rooms
PATCH /api/admin/rooms/{roomId}
GET   /api/admin/bookings
```

Admin can:

- see booking count, successful payments/revenue, room-type count, and remaining inventory;
- edit room price and available inventory;
- see every user's booking;
- inspect the latest payment state/transaction for each booking.

The room edit uses the same pessimistic row lock used by customer booking creation, so an admin inventory update does not bypass Phase 17 concurrency protection.

Android routes users by `session.user.role`:

```text
USER  -> normal hotel booking UI
ADMIN -> Admin Dashboard -> Rooms / Bookings & Payments / Profile
```

### Deployment

The backend is ready for a public Railway + Railway MySQL deployment. See `backend/DEPLOY_RAILWAY.md`.

The production Spring profile now accepts Railway's dynamic `PORT`. Database/JWT/admin credentials remain environment variables.

### Phase 22 - Android Release

Release hardening now includes:

- R8 code shrinking (`isMinifyEnabled = true`);
- resource shrinking (`isShrinkResources = true`);
- explicit Gson DTO keep rules;
- release signing from local `keystore.properties` or CI environment secrets;
- configurable `versionCode` / `versionName`;
- production API URL from `BOOKING_API_PROD_URL`;
- disabled Android backup for the app's locally persisted authentication/session data;
- GitHub `Android Release` workflow that creates signed APK + AAB + R8 mapping artifacts;
- tagged builds (`v1.0.0`, etc.) can publish APK/AAB to GitHub Releases automatically.

## Create/test an admin locally

If you already have a user account, promote it on the next backend startup:

```powershell
cd backend
$env:ADMIN_BOOTSTRAP_ENABLED="true"
$env:ADMIN_EMAIL="your-email@example.com"
.\gradlew bootRun
```

If creating a brand-new admin:

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED="true"
$env:ADMIN_EMAIL="admin@example.com"
$env:ADMIN_PASSWORD="ChangeThis123!"
$env:ADMIN_DISPLAY_NAME="Hotel Admin"
.\gradlew bootRun
```

After startup, **log out and log in again** in the Android app with that account so the refreshed session contains role `ADMIN`. When the account exists, disable bootstrap for later runs:

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED="false"
```

## Local verification order

From project root:

```powershell
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
.\gradlew assembleStaging
.\gradlew assembleRelease
```

From `backend`:

```powershell
.\gradlew test
.\gradlew build
.\gradlew bootRun
```

Then test USER login and ADMIN login separately.

## Local signed release

1. Generate a release keystore from Android Studio: **Build -> Generate Signed Bundle / APK -> Create new**.
2. Copy `keystore.properties.example` to `keystore.properties` and fill in your local values.
3. Set the deployed backend URL:

```powershell
$env:BOOKING_API_PROD_URL="https://your-public-backend.example/"
```

4. Build:

```powershell
.\gradlew clean assembleRelease bundleRelease `
  -PBOOKING_VERSION_NAME=1.0.0 `
  -PBOOKING_VERSION_CODE=1
```

Outputs:

```text
app/build/outputs/apk/release/
app/build/outputs/bundle/release/
app/build/outputs/mapping/release/mapping.txt
```

## GitHub signed release setup

Repository variable:

```text
BOOKING_API_PROD_URL=https://<deployed-backend>/
```

Repository secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Convert a `.jks` to Base64 on Windows:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\AndroidKeys\booking-hotel-release.jks")) |
  Set-Content -NoNewline keystore-base64.txt
```

Paste the file contents into `ANDROID_KEYSTORE_BASE64`, then delete the temporary Base64 file.

To publish a GitHub Release after deployment and signing secrets are configured:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

The `Android Release` workflow builds signed APK/AAB and attaches them to the tagged GitHub Release.

## Google Play note (October 2026)

This phase creates a production-style signed release and GitHub-downloadable APK/AAB. It does **not** automatically publish to Google Play. Google Play currently requires new mobile apps and updates to target Android 16 / API 36. This project intentionally keeps its existing Android toolchain/target behavior stable for this phase; a separate API-36 migration should be done before an actual Play Console submission.
