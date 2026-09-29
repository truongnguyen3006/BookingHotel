# Phase 5 - Room Database + Booking History

This phase adds persistent local booking history using Android Room while keeping room availability and booking creation on the remote REST API.

## Data flow

Room list:

Compose UI -> BookingViewModel -> RoomRepository -> Retrofit -> REST API

Booking:

Compose UI -> BookingViewModel -> RoomRepository -> POST /api/bookings
                                              -> update in-memory room state
                                              -> save successful booking to Room database

History:

Room database -> BookingDao Flow -> RoomRepository -> BookingViewModel -> BookingHistoryScreen

## New components

- `Booking` domain model
- `BookingEntity`
- `BookingDao`
- `BookingDatabase`
- entity/domain mappers
- `DatabaseModule` for Hilt
- persistent `bookingHistory` exposed from `RoomRepository`
- `BookingHistoryScreen`
- History navigation route/button

## Manual test

1. Start the mock server: `node mock-server/server.js`.
2. Run the app and book a room.
3. Open `Lịch sử đặt phòng` and verify the booking appears.
4. Close the app completely and launch it again.
5. Open `Lịch sử đặt phòng` again; the booking should still exist.
6. Book another room and verify newest booking is shown first.
7. Stop the mock server. Existing booking history should still be readable because it comes from Room, while remote room loading/booking should show network errors.

### Why there is a local database ID

The mock server resets its `bookingId` counter when Node is restarted. Room therefore uses an auto-generated `localId` as the local primary key and stores the server ID separately as `remoteBookingId`. This prevents an old local history item from being accidentally overwritten after the mock server restarts.
