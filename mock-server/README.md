# Booking Hotel Mock REST API

Run from the project root:

```powershell
node mock-server/server.js
```

Android Emulator connects to `http://10.0.2.2:8080/`.

## Endpoints

- `GET /health`
- `GET /api/rooms`
- `GET /api/rooms/{id}`
- `POST /api/bookings`
- `GET /api/bookings/{id}`
- `POST /api/bookings/{id}/payment`

### Create booking

```json
{
  "roomId": 1,
  "quantity": 2,
  "checkInDate": 1790726400000,
  "checkOutDate": 1790899200000,
  "guests": 3
}
```

The server calculates the number of nights and total price.

### Payment

```json
{
  "method": "CARD",
  "simulateFailure": false,
  "idempotencyKey": "a-client-generated-uuid"
}
```

The payment flow supports `PROCESSING`, `FAILED`, and `SUCCESS`. Repeating the same payment request with the same idempotency key returns the same result instead of creating a duplicate transaction.
