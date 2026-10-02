# Phase 1 — Booking & Payment Logic Hardening

Patch này chỉ xử lý lifecycle booking/payment và inventory. Không đổi currency/money model; phần đó để Phase 2.

## Flow sau Phase 1

```text
Create booking
    |
    v
PENDING_PAYMENT  -- inventory reserved
    |
    +-- timeout ------------------------------+
    |                                         |
    v                                         v
PROCESSING -------------------------------> FAILED
    |                                         |
    | payment success                         +-- inventory released exactly once
    v
SUCCESS
    |
    +-- inventory remains consumed

FAILED -- retry --> reserve inventory again under room lock
                   |
                   +-- enough rooms -> PENDING_PAYMENT -> PROCESSING
                   +-- not enough   -> ROOM_UNAVAILABLE
```

## Những gì đã sửa

### 1. Fix inventory leak
- CARD/QR simulated failure trả lại đúng `booking.quantity` vào `RoomEntity.availableRooms`.
- VNPAY failed IPN trả inventory.
- VNPAY confirmation timeout trả inventory.
- Booking bị bỏ dở quá hạn sẽ được scheduler trả inventory.
- `inventory_released` bảo đảm cùng một booking không restore inventory hai lần.

### 2. Scheduled expiration cho booking bỏ dở
Booking mới có `reservation_expires_at`.

Mặc định:

```text
BOOKING_RESERVATION_MINUTES=15
BOOKING_EXPIRATION_SCAN_MS=60000
```

Scheduler quét `PENDING_PAYMENT` / `PROCESSING` đã quá hạn. Nếu payment vẫn `PENDING`, payment được chuyển thành `FAILED/EXPIRED` trước khi inventory được release.

### 3. VNPAY có confirmation grace period
VNPAY URL hết hạn sau `VNP_EXPIRE_MINUTES`, nhưng backend giữ reservation thêm một khoảng ngắn để IPN đến trễ không race với việc trả inventory.

```text
VNP_CONFIRMATION_GRACE_SECONDS=120
```

Trong grace period, URL thanh toán cũ không được mở lại nhưng inventory vẫn được giữ. Hết grace period mới đánh dấu `EXPIRED` và release inventory.

### 4. Retry an toàn
Retry booking `FAILED` phải reserve inventory lại dưới `PESSIMISTIC_WRITE` room lock.

Nếu số phòng đã được người khác đặt trong lúc booking cũ FAILED:

```text
ROOM_UNAVAILABLE
```

Backend không oversell.

### 5. Chuẩn hóa state machine
Booking transition quan trọng được gom vào `BookingLifecycleService` thay vì set trạng thái rải rác.

Invariant:

```text
PENDING_PAYMENT -> inventory reserved
PROCESSING      -> inventory reserved
SUCCESS         -> inventory consumed
FAILED          -> inventory released
```

Không cho phép chuyển booking sang `SUCCESS` nếu inventory đã release.

VNPAY provider payment cũng chỉ được complete từ `PENDING` sang trạng thái terminal `SUCCESS` hoặc `FAILED`; terminal payment không được complete lần hai.

### 6. Flyway V4
Migration:

```text
V4__booking_reservation_lifecycle.sql
```

thêm:

```text
bookings.inventory_released
bookings.reservation_expires_at
```

và repair một lần các booking `FAILED` cũ từ implementation trước Phase 1 vốn đã trừ inventory nhưng chưa restore.

> Nếu bạn từng sửa thủ công `rooms.available_rooms` trên Railway để bù inventory leak, kiểm tra lại room count sau khi V4 chạy để tránh số phòng bị cộng dư.

## Test được bổ sung

- `BookingLifecycleServiceTest`
  - FAILED restore đúng một lần.
  - retry reserve lại inventory.
  - retry bị từ chối nếu hết phòng.
  - booking đã release inventory không thể chuyển thẳng sang SUCCESS.
- `BookingExpirationServiceTest`
  - booking/payment hết hạn -> FAILED + release.
  - payment SUCCESS không release inventory.
  - reservation chưa hết hạn không bị động tới.
- `PaymentServiceLifecycleTest`
  - CARD/QR success/failure đi qua lifecycle service.
- `PaymentStateMachineTest`
  - provider payment chỉ complete một lần.
- `VnPayLifecycleTest`
  - IPN success giữ inventory consumed.
  - IPN failure release inventory.
  - duplicate terminal IPN không apply inventory transition lần hai.

## Cách apply patch

Commit bản đang chạy ổn trước:

```powershell
cd D:\BookingHotel2\BookingHotel2

git add .
git commit -m "stable before phase 1 booking lifecycle hardening"
git push
```

Giải nén ZIP patch rồi copy **nội dung bên trong patch** vào:

```text
D:\BookingHotel2\BookingHotel2\
```

chọn Merge/Replace.

## Test backend

```powershell
cd D:\BookingHotel2\BookingHotel2\backend

.\gradlew test
.\gradlew build
```

Sau đó chạy local:

```powershell
.\gradlew bootRun
```

Flyway phải apply:

```text
V4__booking_reservation_lifecycle.sql
```

### Test expiration nhanh

Có thể tạm chạy:

```powershell
$env:BOOKING_RESERVATION_MINUTES="1"
$env:BOOKING_EXPIRATION_SCAN_MS="5000"
.\gradlew bootRun
```

Tạo booking nhưng không thanh toán. Sau khoảng 1 phút:

```text
Booking -> FAILED
available_rooms -> tăng lại đúng quantity đã giữ
```

Refresh nhiều lần hoặc để scheduler chạy tiếp cũng không được cộng inventory lần hai.

Sau test, mở terminal mới hoặc xóa các biến tạm.

## Railway

Không có secret mới.

Các biến mới đều có default, nên có thể không thêm gì. Nếu muốn cấu hình rõ ràng:

```text
BOOKING_RESERVATION_MINUTES=15
BOOKING_EXPIRATION_SCAN_MS=60000
VNP_CONFIRMATION_GRACE_SECONDS=120
```

Push code để Railway redeploy. Sau khi deploy, kiểm tra health endpoint cũ vẫn `UP`, rồi test một booking bỏ dở và một VNPAY failure/timeout.

## Lưu ý build trong môi trường tạo patch

Static review và kiểm tra cấu trúc patch đã được thực hiện. Môi trường tạo patch không có Gradle distribution cache và không có network để tải `gradle-8.14.4`, nên full `gradlew test/build` cần được xác nhận trên máy của bạn.
