# Comprehensive Audit Report for Booking Hotel

### 1. Executive Summary

- **Total Critical issues**: 2
- **Total High issues**: 3
- **Total Medium issues**: 4
- **Total Low issues**: 4
- **Overall Consistency Assessment**: The project demonstrates a strong technical foundation with a robust tech stack (Compose, MVVM, Room, WorkManager, Spring Boot, Spring Security). The implementation of pessimistic locking for booking creation and VNPAY HMAC-SHA512 verification are both handled correctly. However, there are significant logic gaps in edge-case handling, particularly regarding abandoned/failed payments permanently leaking room inventory. There are also critical thread-safety issues in the OkHttp TokenAuthenticator and several disconnected UX flows (unhandled deep links, missing payment resume buttons, and currency transparency issues) that prevent this from being fully production-ready.

### Audit Findings Table

| ID | Severity | Area | File | Line/Method | Problem | Why it matters | Reproduction/Scenario | Recommended direction |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **01** | CRITICAL | Booking State | `PaymentService.java`, `VnPayService.java` | `pay()`, `processIpn()`, `expirePayment()` | Failed/Expired payments do not restore `RoomEntity.availableRooms`. | Permanent inventory loss. Rooms remain locked indefinitely without revenue if a user abandons a payment. | User books room (inventory drops). User closes VNPAY or IPN returns FAILED. Room is permanently subtracted from total inventory. | Update payment failure callbacks and add a `@Scheduled` backend job to expire stale `PENDING_PAYMENT` bookings and increment room inventory. |
| **02** | CRITICAL | Auth / Concurrency | `TokenAuthenticator.kt` | `authenticate()` | Missing `Mutex` around `authApi.refresh()`. | Concurrent 401s cause duplicate refresh calls. Backend revokes token on first use, causing the second request to fail and log the user out. | 2 parallel image/API requests get 401. Thread A refreshes successfully. Thread B tries to refresh the old token, gets rejected, and clears session. | Wrap the refresh logic in a `Mutex` to serialize refresh attempts. Threads should wait and reuse the new token if already refreshed. |
| **03** | HIGH | UI/UX | `BookingHistoryScreen.kt` | `BookingHistoryItem()` | No "Thanh toán" (Pay) button for `PENDING_PAYMENT` bookings. | If the app crashes or the user exits the payment screen, they are permanently locked out of paying. | User creates booking -> backs out of `PaymentScreen`. User opens History -> sees "Chờ thanh toán" but cannot click it to resume VNPAY. | Add a "Thanh toán" action for `PENDING_PAYMENT` status in the history list that navigates to `PaymentScreen`. |
| **04** | HIGH | Offline / WorkManager | `BackgroundSyncWorker.kt` | `doWork()` | Sync aborts entirely if public room catalog sync fails. | Booking history won't sync in the background if the public room API has a temporary hiccup. | `roomRepository.syncRoomsFromNetwork()` throws 500. `refreshBookingHistory()` is skipped entirely. | Isolate sync blocks using `try-catch` so user history attempts to sync regardless of public catalog success. |
| **05** | HIGH | Currency / UI | `RoomDetailsScreen.kt`, `PaymentScreen.kt` | Multiple | UI displays USD-like formatting (e.g., $150.00), but VNPAY Sandbox charges VND. | Users are unaware of the exchange rate or the fact they will be charged in VND until they leave the app. | User sees "$150.00". Clicks "Mở VNPAY". Sees "3,750,000 VND" on the VNPAY web interface. | Add a UI element showing the conversion rate (`VNP_VND_PER_PRICE_UNIT`) or format prices natively in VND across the app. |
| **06** | MEDIUM | VNPAY / UI | `MainActivity.kt` | `onNewIntent()` | The `bookinghotel://payment-result` deep link from VNPAY is intercepted but completely ignored. | Users returning from the browser must manually click "Kiểm tra kết quả" instead of having it auto-verify. | VNPAY redirects to `bookinghotel://...`. App opens, intent is passed, but no code extracts `txnRef` or auto-triggers status check. | Handle the deep link data via `navController.handleDeepLink(intent)` or pass it to `BookingViewModel`. |
| **07** | MEDIUM | VNPAY | `VnPayService.java` | `toVndAmount()` | Recalculates `amountVnd` on every retry using current config. | If the admin updates the exchange rate environment variable, retrying an old booking will charge a different amount. | `VNP_VND_PER_PRICE_UNIT` changes from 25000 to 26000. User retries a $100 booking. Charge jumps from 2.5m to 2.6m VND. | Persist the calculated `amountVnd` in `BookingEntity` at creation, or snapshot the rate, rather than recalculating on retry. |
| **08** | MEDIUM | Test Quality | `BookingFlowUiTest.kt` | Line 33, 53 | Brittle UI tests depend on exact Vietnamese hardcoded text. | Any minor copy change by product owners will break the CI pipeline. | Text changes from "Đặt phòng thành công" to "Hoàn tất đặt phòng" -> Build fails. | Use `testTag` for assertions rather than hardcoded string matching. |
| **09** | LOW | UI/UX | Various Compose files | Multiple | Extensive use of hardcoded strings in Compose files. | App cannot be localized easily and maintaining consistent copy is difficult. | File `RoomListScreen.kt` contains hardcoded "Tìm loại phòng", "Còn phòng", etc. | Extract all hardcoded Vietnamese text to `res/values/strings.xml`. |
| **10** | LOW | Admin Dashboard | `AdminService.java` | `getDashboard()` | Available inventory metric drifts from reality due to the inventory leak bug. | Admin dashboard is misleading. | Abandoned bookings accumulate. `getDashboard()` queries `RoomEntity.availableRooms` which never recovered. | Fixing the inventory leak (Issue #1) will automatically resolve this. |
| **11** | LOW | UI/UX | `RoomDetailsScreen.kt` | `Button(onClick = { showConfirmDialog = true })` | Confirm dialog dismisses instantly, exposing the main screen loading state underneath. | Visual jumpiness. | Click "Xác nhận". Dialog closes immediately. User briefly sees main screen loading spinner instead of a smooth transition. | Keep dialog open with a disabled state/spinner while booking, then navigate directly to summary. |
| **12** | LOW | CI/CD | `backend-ci-cd.yml` | Line 23 | Hardcoded `chmod +x gradlew` step. | Indicates `gradlew` permissions are not tracked in Git. | Windows developers committing code often lose the executable bit, requiring this CI hack. | Run `git update-index --chmod=+x gradlew` locally and commit to permanently fix. |

### 2. Confirmed logic bugs
- **Permanent Inventory Leak**: The most severe bug. `BookingService` correctly implements pessimistic locking and subtracts inventory upon creating a `PENDING_PAYMENT` booking. However, `VnPayService` and `PaymentService` never restore the room count when marking a payment/booking as `FAILED`.
- **Missing Expiration Job**: There is no Cron/scheduled job on the backend to automatically prune `PENDING_PAYMENT` bookings that the user abandoned without a final IPN callback.
- **WorkManager Isolation**: `BackgroundSyncWorker` bails out early if `syncRoomsFromNetwork()` fails, completely skipping the authenticated `refreshBookingHistory()` sync, which should be treated as an independent operation.

### 3. UI/UX inconsistencies
- **Missing Resume Payment Button**: The `BookingHistoryScreen` accurately lists `PENDING_PAYMENT` bookings but offers no action to resume them. If the user exits `PaymentScreen`, the booking is essentially soft-locked.
- **Deep Link Ignored**: VNPAY redirects the user back to the app via `bookinghotel://payment-result`. The Android manifest catches this, and `MainActivity.onNewIntent()` fires, but the data is completely ignored. The user must manually click a "Kiểm tra kết quả" button.
- **Hardcoded Strings**: Nearly all screens (`AuthScreen`, `RoomListScreen`, `PaymentScreen`) use hardcoded Vietnamese strings rather than `stringResource()`.

### 4. Android architecture problems
- **Thread-Safety in TokenAuthenticator**: `TokenAuthenticator.authenticate()` uses `runBlocking` to refresh the token, but lacks a `Mutex`. In a highly concurrent environment (e.g., launching the app and firing 3 API calls at once), all 3 might get 401s and attempt to refresh simultaneously. Because `RefreshTokenService.consumeAndRotate` revokes the token instantly on the backend, the parallel requests will fail, forcing a hard logout.

### 5. Backend/API problems
- **State Cleanup**: As mentioned, the lack of a background cleanup task for stale bookings is the primary backend issue.
- **Admin Dashboard Drift**: `AdminDashboardResponse` calculates `availableInventory` by summing `RoomEntity.getAvailableRooms()`. This value will drift lower and lower over time as abandoned bookings permanently consume inventory.

### 6. VNPAY/payment problems
- **Floating Retry Amounts**: `VnPayService` recalculates the VND amount dynamically based on current configuration on every payment attempt. If the exchange rate configuration is updated, the user's retry attempt will cost a different amount than their original booking.
- **Sandbox Fallback IP**: VNPAY requires an IPv4 address. The backend correctly detects if Railway provides an IPv6 address and gracefully falls back to `127.0.0.1` for sandbox mode, which is implemented well.

### 7. Currency/money problems
- **UI Disconnect**: The Android app calculates and displays totals as generic doubles representing USD (e.g., $150.00). The backend configures `VNP_VND_PER_PRICE_UNIT=25000` to convert this to Vietnamese Dong for VNPAY. The user has no visibility into this conversion inside the app until they are redirected to the VNPAY web portal, creating a confusing financial UX.

### 8. Authentication/security problems
- **IDOR Protections**: The backend utilizes `findOwnedByIdForUpdate` and `findByIdAndUser_Id` extensively. IDOR protection is properly implemented; users cannot access or pay for other users' bookings.
- **Admin Security**: The `@PatchMapping` and `@GetMapping` in `AdminController` are correctly secured by `SecurityConfig` demanding `ROLE_ADMIN`.

### 9. Offline/cache/WorkManager problems
- **Logout Isolation**: When a user logs out, `RetrofitAuthRepository` explicitly calls `bookingDao.clearBookings()`. This successfully ensures that User A's history is not visible when User B logs in. The implementation here is sound.

### 10. Database/Flyway problems
- The Flyway migrations accurately reflect the JPA entities. Constraints like `chk_rooms_available CHECK (available_rooms >= 0)` prevent database-level overselling. (Ironically, this constraint protects against negative inventory but exacerbates the inventory leak bug).

### 11. CI/CD and release problems
- The GitHub actions workflows are structurally sound. However, the presence of `chmod +x gradlew` in the CI scripts indicates that file execution permissions are not properly tracked in Git.

### 12. Missing/outdated tests
- **Missing Token Refresh Concurrency Tests**: There are no Android tests verifying what happens when `TokenAuthenticator` is hit by multiple threads simultaneously.
- **Missing Expiration Tests**: There are no backend tests verifying that an expired/failed payment releases the inventory lock.
- **Brittle UI Tests**: `BookingFlowUiTest.kt` relies on exact string matches (e.g., `"Số lượng đặt phòng tối thiểu là 1."`), making the CI pipeline fragile to copy changes.

### 13. Dead/obsolete code
- No significant dead code was found; the repository is relatively lean and highly focused on the booking flow.

### 14. What is already implemented correctly
- **Pessimistic Locking**: The use of `@Transactional` with `findByIdForUpdate()` in both `BookingService` and `AdminService` ensures absolute safety against overselling rooms concurrently.
- **VNPAY HMAC Signatures**: The implementation of `VnPaySigner.verify()` and checksum creation correctly sorts parameters and protects against IPN spoofing.
- **Offline-First Room Catalog**: `RetrofitRoomRepository` correctly falls back to cached rooms when the network is unavailable, hiding the failure from the user while keeping the app functional.

### 15. Recommended fix priority

**P0 - Must fix before release**
- Fix the inventory leak in `PaymentService` and `VnPayService` by incrementing `RoomEntity.availableRooms` when a payment fails/expires.
- Add a `@Scheduled` task to the backend to automatically expire `PENDING_PAYMENT` bookings older than 15 minutes.
- Add a `Mutex` to `TokenAuthenticator.kt` to serialize token refresh calls.

**P1 - Should fix before portfolio/demo**
- Add a "Thanh toán" button to `BookingHistoryScreen` for `PENDING_PAYMENT` bookings.
- Handle the `bookinghotel://payment-result` deep link in `MainActivity.kt` so users don't have to manually click to verify VNPAY results.
- Add a conversion disclaimer to the Android UI explaining the USD to VND conversion rate before opening VNPAY.

**P2 - Worthwhile improvement**
- Split the WorkManager `try-catch` blocks so public room sync failures do not halt booking history syncs.
- Extract all Vietnamese text to `strings.xml`.
- Fix the `chmod +x gradlew` issue by committing the file permission to git.

**P3 - Optional cleanup**
- Snapshot the `amountVnd` at booking creation time rather than recalculating it dynamically on payment retries to guarantee price stability.
- Update UI Tests to use `testTag` instead of literal string matching.

AUDIT COMPLETE — NO SOURCE FILES MODIFIED
