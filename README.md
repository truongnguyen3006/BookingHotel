# BookingHotel

[![Android CI](https://github.com/truongnguyen3006/BookingHotel/actions/workflows/android-ci.yml/badge.svg)](https://github.com/truongnguyen3006/BookingHotel/actions/workflows/android-ci.yml)
[![Backend CI/CD](https://github.com/truongnguyen3006/BookingHotel/actions/workflows/backend-ci-cd.yml/badge.svg)](https://github.com/truongnguyen3006/BookingHotel/actions/workflows/backend-ci-cd.yml)

Ứng dụng đặt phòng khách sạn full-stack gồm **Android native** và **Spring Boot backend**, được xây dựng theo hướng production-ready cho mục đích portfolio.

Dự án tập trung vào các bài toán thực tế như xác thực JWT, đặt phòng và quản lý tồn kho, thanh toán VNPAY, xử lý race condition, đồng bộ dữ liệu offline-first và triển khai backend thực tế.

## Tải APK

**[Tải BookingHotel phiên bản mới nhất](https://github.com/truongnguyen3006/BookingHotel/releases/latest)**

> Ứng dụng sử dụng **VNPAY Sandbox** để demo thanh toán. Không có giao dịch tiền thật.

## Giao diện ứng dụng

> **Cần chèn 5 ảnh trước khi hoàn thiện README.**
>
> Tạo thư mục `docs/screenshots/` và thêm:
>
> - `room-list.png` — danh sách phòng, nên thấy rõ card phòng, giá VND và số phòng còn lại
> - `booking.png` — xác nhận đặt phòng, nên thấy ngày nhận/trả, số khách, số phòng và tổng tiền
> - `vnpay.png` — thanh toán VNPAY hoặc màn hình thanh toán thành công, không để lộ dữ liệu cá nhân
> - `history.png` — lịch sử booking, nên thấy rõ trạng thái booking/payment
> - `admin.png` — màn hình quản trị, ưu tiên ảnh thể hiện rõ việc quản lý phòng, booking hoặc payment
>
> Ảnh Android nên chụp cùng một thiết bị/emulator, cùng kích thước và theo chiều dọc.

| Danh sách phòng | Xác nhận đặt phòng |
|---|---|
| _Chèn `docs/screenshots/room-list.png`_ | _Chèn `docs/screenshots/booking.png`_ |

| Thanh toán VNPAY | Lịch sử booking |
|---|---|
| _Chèn `docs/screenshots/vnpay.png`_ | _Chèn `docs/screenshots/history.png`_ |

### Quản trị

_Chèn `docs/screenshots/admin.png`_

<!--
Sau khi đã thêm ảnh vào docs/screenshots/, thay các placeholder phía trên bằng:

| Danh sách phòng | Xác nhận đặt phòng |
|---|---|
| <img src="docs/screenshots/room-list.png" width="280"/> | <img src="docs/screenshots/booking.png" width="280"/> |

| Thanh toán VNPAY | Lịch sử booking |
|---|---|
| <img src="docs/screenshots/vnpay.png" width="280"/> | <img src="docs/screenshots/history.png" width="280"/> |

### Quản trị

<p align="center">
  <img src="docs/screenshots/admin.png" width="280"/>
</p>
-->

## Tính năng chính

### Người dùng

- Đăng ký, đăng nhập và đăng xuất
- JWT access token và rotating refresh token
- Tự động refresh token khi nhiều request gặp `401`
- Xem danh sách phòng, tìm kiếm, lọc và sắp xếp
- Xem chi tiết phòng
- Chọn ngày nhận/trả phòng, số khách và số lượng phòng
- Tạo booking và giữ tồn kho
- Xem lịch sử booking
- Theo dõi trạng thái booking và payment
- Thanh toán bằng VNPAY Sandbox
- Deep link quay lại ứng dụng sau thanh toán
- Tự động hoàn trả tồn kho khi thanh toán thất bại hoặc booking hết hạn
- Offline-first cache bằng Room
- Đồng bộ nền bằng WorkManager
- Cô lập dữ liệu theo session người dùng

### Quản trị

- Quản lý thông tin phòng và số lượng phòng khả dụng
- Theo dõi danh sách booking
- Theo dõi trạng thái booking và payment
- Theo dõi thông tin thanh toán phục vụ quản lý hệ thống
- Dữ liệu quản trị sử dụng cùng backend và cơ sở dữ liệu với ứng dụng người dùng

## Công nghệ sử dụng

### Android

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel + StateFlow
- Hilt
- Retrofit + OkHttp
- Room
- DataStore
- WorkManager

### Backend

- Java 17
- Spring Boot
- Spring Security
- JWT
- Spring Data JPA / Hibernate
- MySQL
- Flyway
- Testcontainers

## Kiến trúc hệ thống

```mermaid
flowchart TB
    A["Ứng dụng Android"]

    C["Room Database"]
    D["DataStore"]
    E["WorkManager"]

    B["Spring Boot REST API"]
    F["Spring Security + JWT"]
    G["JPA / Hibernate"]
    I["Flyway"]

    H[("MySQL")]
    J["VNPAY Sandbox"]

    A -->|"HTTPS / Retrofit"| B

    A --> C
    A --> D
    A --> E

    B --> F
    B --> G
    G --> H
    B --> I
    I --> H

    B -->|"Tạo giao dịch"| J
    J -->|"Return / IPN"| B
```

## Điểm kỹ thuật nổi bật

- Dùng pessimistic locking cho các luồng booking/payment cần đảm bảo đồng thời
- Payment sử dụng idempotency key
- Chỉ cho phép một payment `PENDING` hoạt động trên mỗi booking
- Inventory được hoàn trả đúng một lần khi booking thất bại hoặc hết hạn
- VNPAY IPN được xử lý trong transaction và có bảo vệ callback đến trễ
- Giá booking được lưu dưới dạng snapshot VND bằng kiểu số nguyên
- Token refresh dùng cơ chế single-flight để tránh race condition
- Cache booking được cô lập theo session để tránh rò dữ liệu giữa các tài khoản

## Cấu trúc project

```text
BookingHotel/
├── .github/       # GitHub Actions
├── app/           # Android app
├── backend/       # Spring Boot backend
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Chạy project ở local

### Backend

```powershell
cd backend
docker compose up -d
.\gradlew.bat bootRun
```

### Android

Mở project bằng Android Studio, chọn build variant `debug` và chạy trên emulator hoặc thiết bị thật.

Android Emulator mặc định kết nối backend local qua:

```text
http://10.0.2.2:8080/
```

## Kiểm thử

### Android

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

### Backend

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat build
```

## Triển khai

- Backend và MySQL được triển khai trên Railway
- Android release tắt CARD/QR demo và sử dụng backend production
- CI/CD được thực hiện bằng GitHub Actions
- APK release đã được kiểm thử trên thiết bị Android thật, bao gồm luồng VNPAY Sandbox

---

Dự án được xây dựng như một portfolio **Android + Java Backend**, tập trung vào tính đúng đắn của transaction, xử lý đồng thời, bảo mật phiên đăng nhập và vòng đời thanh toán.
