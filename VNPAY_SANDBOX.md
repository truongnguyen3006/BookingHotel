# VNPAY Sandbox integration — BookingHotel

This phase upgrades the existing simulated payment flow with a real VNPAY Sandbox redirect + IPN confirmation flow.

## Architecture

```text
Android
  -> POST /api/bookings/{id}/payment/vnpay
Spring Boot
  -> signs VNPAY 2.1.0 request with HMAC-SHA512
Android/browser
  -> VNPAY Sandbox checkout
VNPAY
  -> GET /api/payments/vnpay/ipn        (server-to-server, authoritative)
  -> GET /api/payments/vnpay/return     (browser return only)
Spring Boot
  -> verifies checksum + amount + transaction state
  -> updates MySQL payment + booking
Android
  -> GET /api/bookings/{id}/payment/vnpay/status
```

The Return URL never marks a booking paid. Only a valid VNPAY IPN may move the payment to `SUCCESS`.

## Railway variables

Set these in the **backend service -> Variables** tab. Never commit the real HashSecret.

```text
VNP_TMN_CODE=B0X8KC9I
VNP_HASH_SECRET=<your sandbox HashSecret>
VNP_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNP_RETURN_URL=https://bookinghotel-production.up.railway.app/api/payments/vnpay/return
VNP_EXPIRE_MINUTES=15
VNP_CONFIRMATION_GRACE_SECONDS=120
BOOKING_RESERVATION_MINUTES=15
BOOKING_EXPIRATION_SCAN_MS=60000
```

The catalog now stores native VND as integer `Long/BIGINT` values end-to-end. `VNP_VND_PER_PRICE_UNIT` has been removed and must also be removed from Railway. A room price such as `1,250,000` is already VND; when a VNPAY request is signed, only the protocol field is scaled as `vnp_Amount = amountVnd * 100`.

After adding/changing variables, redeploy the backend. Flyway will apply the pending migrations automatically. In the combined v1.0.0 patch, `V4__booking_reservation_lifecycle.sql` hardens booking inventory lifecycle and `V5__money_vnd_bigint.sql` migrates legacy price units to native VND `BIGINT`.

## VNPAY IPN URL

Provide/configure this HTTPS endpoint for the sandbox merchant:

```text
https://bookinghotel-production.up.railway.app/api/payments/vnpay/ipn
```

VNPAY's integration guide requires the merchant to implement an HTTPS IPN URL and provide it to VNPAY after implementation. If the sandbox portal does not expose a field for it, send this exact URL to the sandbox/integration contact that issued the merchant credentials.

The browser Return URL is:

```text
https://bookinghotel-production.up.railway.app/api/payments/vnpay/return
```

## Backend endpoints

```text
POST /api/bookings/{bookingId}/payment/vnpay
GET  /api/bookings/{bookingId}/payment/vnpay/status
GET  /api/payments/vnpay/ipn
GET  /api/payments/vnpay/return
```

The first two require the logged-in user and enforce booking ownership. IPN/Return are public because VNPAY must call them, but all callback data is checksum-verified.

## Payment safety

- HMAC-SHA512 checksum verification.
- `vnp_TmnCode` validation.
- Amount validation against the amount stored when the payment was created.
- Pessimistic booking/payment locks during payment creation/IPN update.
- Existing idempotency key protection is preserved.
- A still-active VNPAY attempt is reused instead of creating parallel provider transactions.
- Expired attempts become `FAILED`; retry uses a new attempt.
- Repeated IPN for an already-confirmed payment returns `RspCode=02`.
- Existing CARD/QR simulated payment remains available for UI/testing demonstrations.

## Local verification before redeploy

Android root:

```powershell
cd D:\BookingHotel2\BookingHotel2
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
```

Optional emulator tests:

```powershell
.\gradlew connectedDebugAndroidTest
```

Backend:

```powershell
cd D:\BookingHotel2\BookingHotel2\backend
.\gradlew test
.\gradlew build
```

Then push to GitHub and let Railway redeploy.

## Production APK test

Build the Android release against Railway:

```powershell
cd D:\BookingHotel2\BookingHotel2
$env:BOOKING_API_PROD_URL="https://bookinghotel-production.up.railway.app/"
.\gradlew assembleRelease
```

Use the signed release APK on a real Android phone. The local Spring Boot server can be stopped while testing.

## Official VNPAY sandbox success card

VNPAY's sandbox demo currently lists:

```text
Bank: NCB
Card number: 9704198526191432198
Cardholder: NGUYEN VAN A
Issue date: 07/15
OTP: 123456
```

This is sandbox data only; it does not charge real money.

## Expected end-to-end flow

1. Login as a normal user.
2. Create a booking.
3. On Payment, select `VNPAY`.
4. Confirm -> browser opens VNPAY Sandbox.
5. Complete the sandbox payment.
6. Return to BookingHotel. The `bookinghotel://payment-result` deep link triggers an automatic backend status refresh.
7. If needed, History/Payment also provides **Kiểm tra kết quả VNPAY**.
8. Expected: `SUCCESS`, VNPAY transaction id, payment time, booking history updated.
9. In MySQL/Railway, the `payments` row should contain `provider_reference`, `provider_response_code=00`, `provider_transaction_status=00`, `amount_vnd`, and `paid_at`.

If the browser says success but the Android status remains `PENDING`, check whether VNPAY has been configured with the IPN URL above. The Return URL is deliberately not authoritative.

## Official references

- VNPAY PAY integration guide: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
- VNPAY sandbox demo/test cards: https://sandbox.vnpayment.vn/apis/vnpay-demo/
