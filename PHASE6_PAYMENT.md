# Phase 6 - Simulated Payment Flow

## Goal
Add a realistic demo payment workflow on top of the existing booking flow without integrating a real payment provider.

The Android app now uses Retrofit to call the local mock REST API for payment processing. The payment result is persisted back into the existing Room booking history.

## Flow

Booking Summary -> Payment Screen -> POST /api/bookings/{bookingId}/payment

The screen supports:
- CARD and QR payment methods.
- SUCCESS result with a generated transaction id.
- FAILED business result for demo/testing.
- Retry after a simulated failed payment.
- Network/API errors handled separately from a declined payment.
- Room booking history automatically updated from PENDING_PAYMENT to FAILED or SUCCESS.

## Important implementation detail
Room uses `localId` as the local primary key while the mock server uses `bookingId` as its remote id.
Payment updates the local Room row by `localId`, not by remote booking id. This prevents an old local history row from being updated accidentally if the mock server is restarted and starts generating booking ids from 1 again.

## Mock API

### POST /api/bookings/{bookingId}/payment

Request:
```json
{
  "method": "CARD",
  "simulateFailure": false
}
```

Success response:
```json
{
  "bookingId": 1,
  "status": "SUCCESS",
  "method": "CARD",
  "transactionId": "TXN-1-...",
  "message": "Payment completed successfully"
}
```

Simulated decline:
```json
{
  "bookingId": 1,
  "status": "FAILED",
  "method": "CARD",
  "transactionId": null,
  "message": "Payment was declined (simulated)"
}
```

The `simulateFailure` switch exists only to make the FAILED -> Retry flow deterministic during development/demo.

## Manual test checklist
1. Start `node mock-server/server.js`.
2. Launch the Android app and create a booking.
3. From Booking Summary, tap `Thanh toán ngay`.
4. Select CARD or QR.
5. Leave `Mô phỏng thanh toán thất bại` OFF and pay.
6. Verify SUCCESS and a transaction id are shown.
7. Open booking history and verify status is `Thành công`.
8. Create a second booking.
9. Turn the failure simulation ON and pay.
10. Verify FAILED is shown and booking history becomes `Thất bại`.
11. Turn failure simulation OFF and retry.
12. Verify it becomes SUCCESS and Room history updates automatically.
13. Stop the mock server and attempt payment on a new booking; verify a connection error is shown instead of a crash.

## Phase boundary
This phase deliberately does not add real card data, payment SDKs, or production payment credentials. It is a mock payment state machine for demonstrating Retrofit, repository coordination, error states, retry, and local persistence.
