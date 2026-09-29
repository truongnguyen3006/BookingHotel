# Local Mock Hotel REST API

This lightweight Node.js server exists only for Phase 3 development so the Android app can make real HTTP requests through Retrofit without depending on a third-party API.

## Run

From the project root:

```powershell
node mock-server/server.js
```

Keep that terminal open while the Android app is running.

The Android Emulator accesses the host machine through `10.0.2.2`, so the debug base URL is:

```text
http://10.0.2.2:8080/
```

## Endpoints

- `GET /health`
- `GET /api/rooms`
- `GET /api/rooms/{id}`
- `POST /api/bookings`
- `GET /api/bookings/{id}`

Example booking body:

```json
{
  "roomId": 1,
  "quantity": 2
}
```

The server stores data in memory. Restarting it resets room availability to 10 for each room.
