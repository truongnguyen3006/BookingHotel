# Phase 4 - Hilt Dependency Injection

Phase 4 replaces manual dependency creation with Hilt while preserving the Phase 3 Retrofit behavior.

## What changed

- Added `BookingHotelApplication` with `@HiltAndroidApp`.
- Added `@AndroidEntryPoint` to `MainActivity`.
- Added `@HiltViewModel` and constructor injection to `BookingViewModel`.
- Added `NetworkModule` to provide singleton `Retrofit` and `HotelApiService`.
- Added `RepositoryModule` to bind `RetrofitRoomRepository` to `RoomRepository`.
- Added `@Inject` constructor to `RetrofitRoomRepository`.
- `BookingHotelApp` now obtains the ViewModel with `hiltViewModel()`.
- Removed manual `NetworkProvider` dependency creation.
- Added Hilt, kapt, and AndroidX Hilt Navigation Compose dependencies.

## Resulting dependency graph

```text
BookingHotelApplication (@HiltAndroidApp)
        |
        +--> NetworkModule
        |       +--> Retrofit
        |       +--> HotelApiService
        |
        +--> RepositoryModule
                +--> RoomRepository -> RetrofitRoomRepository
                                      |
                                      +--> HotelApiService

MainActivity (@AndroidEntryPoint)
        |
        +--> BookingViewModel (@HiltViewModel)
                |
                +--> RoomRepository
```

## Local test

Keep the Phase 3 mock server running:

```powershell
node mock-server/server.js
```

Then Gradle Sync, rebuild, and run the app. The user-visible behavior should remain unchanged from Phase 3.

Recommended checks:

1. Room list loads from the REST API.
2. Booking decreases server inventory.
3. Returning to the list shows the updated inventory.
4. Stop the mock server and Retry: network error is shown instead of crashing.
5. Start the server again and Retry: rooms load again.

## Important concept for interviews

Before Hilt, `BookingViewModel` manually created its own dependency:

```kotlin
RetrofitRoomRepository(NetworkProvider.hotelApi)
```

After Hilt, the ViewModel only declares what it needs:

```kotlin
@HiltViewModel
class BookingViewModel @Inject constructor(
    private val roomRepository: RoomRepository
) : ViewModel()
```

Hilt builds and supplies the dependency graph. This reduces coupling and makes dependencies easier to replace in tests.
