# Phase 3 - Retrofit + REST API

## Goal

Move the running Android app from in-memory room data to a REST API accessed with Retrofit.

## Request flow

```text
Compose UI
   -> BookingViewModel
   -> RoomRepository
   -> RetrofitRoomRepository
   -> HotelApiService (Retrofit)
   -> Local REST API
```

## What changed

- Added Retrofit and Gson converter dependencies.
- Added Android INTERNET permission.
- Added a debug-only cleartext HTTP allowance for the local development API.
- Added `HotelApiService`.
- Added request/response DTOs and DTO-to-domain mapping.
- Added `RetrofitRoomRepository`.
- Updated `RoomRepository` to support suspend network operations.
- Updated `BookingViewModel` to use `viewModelScope`, loading state, error state and asynchronous booking.
- Updated the list/details screens to display network loading/error state.
- Added a dependency-free local Node.js REST API under `mock-server/`.

## Start the project

1. Start the API:

```powershell
node mock-server/server.js
```

2. In Android Studio, sync Gradle.
3. Run the app on the Android Emulator.
4. The room list should now come from `GET /api/rooms`.
5. Booking calls `POST /api/bookings` and the returned room inventory updates the StateFlow.

## Important

`http://10.0.2.2:8080/` works for the Android Emulator. If you later run the app on a physical phone, replace `API_BASE_URL` in `app/build.gradle.kts` with your computer's LAN IP and ensure both devices are on the same network.

The local server is a development dependency only. A later backend can replace it without changing the Compose UI because the app already depends on the `RoomRepository` abstraction.
