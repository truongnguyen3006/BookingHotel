package com.example.bookinghotel.backend.integration;

import com.example.bookinghotel.backend.api.dto.BookingRequest;
import com.example.bookinghotel.backend.api.dto.PaymentRequest;
import com.example.bookinghotel.backend.domain.*;
import com.example.bookinghotel.backend.repository.*;
import com.example.bookinghotel.backend.service.*;
import com.example.bookinghotel.backend.vnpay.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL locks and transactional proxies. No mocking of repository persistence. */
@Testcontainers
@SpringBootTest(properties = {"spring.profiles.active=local", "booking.expiration-scan-ms=3600000",
        "payment.demo-enabled=true", "vnpay.tmn-code=TEST0001", "vnpay.hash-secret=phase5-test-secret",
        "vnpay.return-url=https://example.invalid/api/payments/vnpay/return"})
class PaymentInventoryIT {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired BookingService bookings;
    @Autowired PaymentService payments;
    @Autowired VnPayService vnpay;
    @Autowired BookingExpirationService expiration;
    @Autowired RoomJpaRepository roomRepo;
    @Autowired BookingJpaRepository bookingRepo;
    @Autowired PaymentJpaRepository paymentRepo;
    @Autowired UserJpaRepository userRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @MockitoSpyBean BookingLifecycleService lifecycle;
    BookingLifecycleService lifecycleTarget;
    MockMvc mvc;
    int roomId;

    @BeforeEach void setUp() {
        lifecycleTarget = AopTestUtils.getUltimateTargetObject(lifecycle);
        reset(lifecycleTarget);
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM bookings");
        if (userRepo.findByEmailIgnoreCase("a@phase5.test").isEmpty()) {
            userRepo.save(new UserEntity("a@phase5.test", "unused", "A", Role.USER, true, Instant.now()));
            userRepo.save(new UserEntity("b@phase5.test", "unused", "B", Role.USER, true, Instant.now()));
        }
        roomId = roomRepo.save(new RoomEntity("standard_room", UUID.randomUUID().toString(),
                1_250_000L, Set.of("Wi-Fi"), 5)).getId();
        signIn("a@phase5.test");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void cleanContext() { SecurityContextHolder.clearContext(); }

    @Test void duplicateSuccessThenFailedCallback_keepsSuccessAndConsumedInventory() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        assertEquals("00", vnpay.processIpn(callback(ref, "00", "00")).get("RspCode"));
        assertEquals("02", vnpay.processIpn(callback(ref, "00", "00")).get("RspCode"));
        assertEquals("02", vnpay.processIpn(callback(ref, "24", "02")).get("RspCode"));
        assertState(id, "SUCCESS", false, 4, "SUCCESS");
    }
    @Test void duplicateFailureThenLateSuccess_releasesExactlyOnce() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        assertEquals("00", vnpay.processIpn(callback(ref, "24", "02")).get("RspCode"));
        assertEquals("02", vnpay.processIpn(callback(ref, "24", "02")).get("RspCode"));
        assertEquals("02", vnpay.processIpn(callback(ref, "00", "00")).get("RspCode"));
        assertState(id, "FAILED", true, 5, "FAILED");
    }
    @Test void lifecycleExceptionRollsBackAlreadyMutatedPayment() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        doThrow(new IllegalStateException("injected after Payment change")).when(lifecycleTarget).markSuccessful(any());
        assertThrows(RuntimeException.class, () -> vnpay.processIpn(callback(ref, "00", "00")));
        assertState(id, "PROCESSING", false, 4, "PENDING");
    }
    @Test void inventoryFailureRollsBackFailedPaymentAndReleasedFlag() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        doThrow(new IllegalStateException("injected room failure")).when(lifecycleTarget).markFailedAndRelease(any());
        assertThrows(RuntimeException.class, () -> vnpay.processIpn(callback(ref, "24", "02")));
        assertState(id, "PROCESSING", false, 4, "PENDING");
    }
    @Test void cardQrAndRetryCannotOverlapActiveVnpay() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        for (String method : List.of("CARD", "QR")) {
            assertThrows(RuntimeException.class, () -> payments.pay(id, new PaymentRequest(method, false, method)));
        }
        assertEquals(ref, vnpay.createPayment(id, "retry", "127.0.0.1").txnRef());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?", Integer.class, id));
        assertState(id, "PROCESSING", false, 4, "PENDING");
    }
    @Test void differentMethodsCannotReuseSameIdempotencyKey() {
        int id = book();
        vnpay.createPayment(id, "same", "127.0.0.1");
        assertThrows(RuntimeException.class, () -> payments.pay(id, new PaymentRequest("CARD", false, "same")));
    }
    @Test void concurrentBookingWithOneRoom_onlyOneSucceeds() throws Exception {
        jdbc.update("UPDATE rooms SET available_rooms=1 WHERE id=?", roomId);
        List<Boolean> results = race(this::tryBook, () -> {
            signIn("b@phase5.test");
            return tryBook();
        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, inventory());
    }
    @Test void schedulerVersusSuccess_neverProducesMixedTerminalStates() throws Exception {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        race(() -> { expiration.expireIfDue(id, Instant.now().plusSeconds(3600)); return true; },
             () -> { vnpay.processIpn(callback(ref, "00", "00")); return true; });
        String status = bookingStatus(id);
        if (status.equals("SUCCESS")) assertState(id, "SUCCESS", false, 4, "SUCCESS");
        else assertState(id, "FAILED", true, 5, "FAILED");
    }
    @Test void concurrentDuplicateIpns_doNotReadStalePendingFromPersistenceContext() throws Exception {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        List<String> results = race(() -> vnpay.processIpn(callback(ref, "24", "02")).get("RspCode"),
                                   () -> vnpay.processIpn(callback(ref, "24", "02")).get("RspCode"));
        assertTrue(results.contains("00"));
        assertTrue(results.contains("02"));
        assertState(id, "FAILED", true, 5, "FAILED");
    }
    @Test void retryVersusScheduler_keepsOneReservationOrExpiresItAtomically() throws Exception {
        int id = book();
        vnpay.createPayment(id, "first", "127.0.0.1");
        jdbc.update("UPDATE payments SET created_at=DATE_SUB(NOW(6), INTERVAL 1 HOUR) WHERE booking_id=?", id);
        jdbc.update("UPDATE bookings SET reservation_expires_at=DATE_SUB(NOW(6), INTERVAL 1 HOUR) WHERE id=?", id);
        race(() -> { vnpay.createPayment(id, "retry", "127.0.0.1"); return true; },
             () -> { expiration.expireIfDue(id, Instant.now()); return true; });
        assertState(id, "PROCESSING", false, 4, "PENDING");
    }
    @Test void retryVersusNewBookingWithOneRoom_onlyOneCanReserve() throws Exception {
        jdbc.update("UPDATE rooms SET available_rooms=1 WHERE id=?", roomId);
        int id = book();
        payments.pay(id, new PaymentRequest("CARD", true, "failed"));
        List<Boolean> results = race(() -> {
            try { vnpay.createPayment(id, "retry", "127.0.0.1"); return true; }
            catch (com.example.bookinghotel.backend.exception.ConflictException e) { return false; }
        }, () -> {
            signIn("b@phase5.test");
            return tryBook();
        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, inventory());
    }
    @Test void simultaneousVnpayCreation_reusesOneAttempt() throws Exception {
        int id = book();
        List<String> refs = race(() -> vnpay.createPayment(id, "device-a", "127.0.0.1").txnRef(),
                                () -> vnpay.createPayment(id, "device-b", "127.0.0.1").txnRef());
        assertEquals(refs.get(0), refs.get(1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?", Integer.class, id));
    }
    @Test void simultaneousSameKeyDemoPayments_returnOneIdempotentReceipt() throws Exception {
        int id = book();
        List<String> receipts = race(() -> payments.pay(id, new PaymentRequest("CARD", false, "same")).transactionId(),
                                    () -> payments.pay(id, new PaymentRequest("CARD", false, "same")).transactionId());
        assertEquals(receipts.get(0), receipts.get(1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?", Integer.class, id));
        assertState(id, "SUCCESS", false, 4, "SUCCESS");
    }
    @Test void concurrentDemoAndVnpayPayment_onlyOneMethodStarts() throws Exception {
        int id = book();
        List<Boolean> attempts = race(() -> {
            try { payments.pay(id, new PaymentRequest("CARD", false, "card")); return true; }
            catch (com.example.bookinghotel.backend.exception.ConflictException e) { return false; }
        }, () -> {
            try { vnpay.createPayment(id, "vnpay", "127.0.0.1"); return true; }
            catch (com.example.bookinghotel.backend.exception.ConflictException e) { return false; }
        });
        assertEquals(1, attempts.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?", Integer.class, id));
        if (bookingStatus(id).equals("SUCCESS")) assertState(id, "SUCCESS", false, 4, "SUCCESS");
        else assertState(id, "PROCESSING", false, 4, "PENDING");
    }
    @Test void expiredPaymentCannotSucceedLater() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        expiration.expireIfDue(id, Instant.now().plusSeconds(3600));
        assertEquals("02", vnpay.processIpn(callback(ref, "00", "00")).get("RspCode"));
        assertState(id, "FAILED", true, 5, "FAILED");
    }
    @Test void orphanPendingCallbackIsRetiredWithoutResurrectingReleasedInventory() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        jdbc.update("UPDATE bookings SET status='FAILED', inventory_released=TRUE WHERE id=?", id);
        jdbc.update("UPDATE rooms SET available_rooms=5 WHERE id=?", roomId);
        assertEquals("02", vnpay.processIpn(callback(ref, "00", "00")).get("RspCode"));
        assertState(id, "FAILED", true, 5, "FAILED");
    }
    @Test void invalidSignatureAmountAndMerchant_leaveReservationUntouched() {
        int id = book();
        String ref = vnpay.createPayment(id, "first", "127.0.0.1").txnRef();
        Map<String,String> invalidSignature = callback(ref, "00", "00");
        invalidSignature.put("vnp_SecureHash", "invalid");
        assertEquals("97", vnpay.processIpn(invalidSignature).get("RspCode"));
        Map<String,String> badAmount = unsigned(ref, "00", "00"); badAmount.put("vnp_Amount", "100");
        assertEquals("04", vnpay.processIpn(sign(badAmount)).get("RspCode"));
        Map<String,String> badMerchant = unsigned(ref, "00", "00"); badMerchant.put("vnp_TmnCode", "OTHER001");
        assertEquals("97", vnpay.processIpn(sign(badMerchant)).get("RspCode"));
        assertState(id, "PROCESSING", false, 4, "PENDING");
    }
    @Test void ownershipAndAdminSecurityAndClientSuccessRegression() throws Exception {
        int id = book();
        vnpay.createPayment(id, "first", "127.0.0.1");
        mvc.perform(get("/api/bookings/"+id).with(user("b@phase5.test").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/"+id+"/payment/vnpay/status").with(user("b@phase5.test").roles("USER"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/"+id+"/payment/vnpay").with(user("b@phase5.test").roles("USER"))
                .contentType("application/json").content("{\"idempotencyKey\":\"attack\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/"+id+"/payment").with(user("b@phase5.test").roles("USER"))
                .contentType("application/json").content("{\"method\":\"CARD\",\"simulateFailure\":false,\"idempotencyKey\":\"attack\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/dashboard").with(user("a@phase5.test").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/"+id).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings/"+id+"/payment").with(user("a@phase5.test").roles("USER"))
                .contentType("application/json").content("{\"method\":\"SUCCESS\",\"idempotencyKey\":\"attack\"}")).andExpect(status().isBadRequest());
        assertState(id, "PROCESSING", false, 4, "PENDING");
    }
    @Test void clientSuppliedPriceDoesNotControlBookingSnapshot() throws Exception {
        mvc.perform(post("/api/bookings").with(user("a@phase5.test").roles("USER"))
                .contentType("application/json").content("{\"roomId\":"+roomId+",\"quantity\":1,\"guests\":1,\"checkInDate\":"+date(1)+",\"checkOutDate\":"+date(2)+",\"totalPrice\":1}"))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.totalPrice").value(1250000));
    }

    int book() { return bookings.createBooking(new BookingRequest(roomId, 1, date(1), date(2), 1)).bookingId(); }
    boolean tryBook() {
        try { book(); return true; }
        catch (com.example.bookinghotel.backend.exception.ConflictException e) { return false; }
    }
    long date(int offset) { return LocalDate.now(ZoneOffset.UTC).plusDays(offset).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(); }
    int inventory() { return jdbc.queryForObject("SELECT available_rooms FROM rooms WHERE id=?", Integer.class, roomId); }
    String bookingStatus(int id) { return jdbc.queryForObject("SELECT status FROM bookings WHERE id=?", String.class, id); }
    void assertState(int id, String status, boolean released, int stock, String paymentStatus) {
        assertEquals(status, bookingStatus(id));
        assertEquals(released, jdbc.queryForObject("SELECT inventory_released FROM bookings WHERE id=?", Boolean.class, id));
        assertEquals(stock, inventory());
        assertEquals(paymentStatus, jdbc.queryForObject("SELECT status FROM payments WHERE booking_id=? ORDER BY id DESC LIMIT 1", String.class, id));
    }
    static void signIn(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "unused", List.of()));
    }
    <T> List<T> race(Supplier<T> a, Supplier<T> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<T>> tasks = new ArrayList<>();
            for (Supplier<T> work : List.of(a,b)) tasks.add(pool.submit(() -> {
                signIn("a@phase5.test"); ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race start timeout");
                try { return work.get(); } finally { SecurityContextHolder.clearContext(); }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            return List.of(tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    Map<String,String> callback(String ref, String code, String state) { return sign(unsigned(ref,code,state)); }
    Map<String,String> unsigned(String ref, String code, String state) {
        Map<String,String> p = new LinkedHashMap<>();
        p.put("vnp_TmnCode", "TEST0001"); p.put("vnp_TxnRef",ref); p.put("vnp_Amount","125000000");
        p.put("vnp_ResponseCode",code); p.put("vnp_TransactionStatus",state); p.put("vnp_TransactionNo","transaction-1");
        return p;
    }
    Map<String,String> sign(Map<String,String> p) {
        Map<String,String> result = new LinkedHashMap<>();
        for (String part : VnPaySigner.buildSignedQuery(p,"phase5-test-secret").split("&")) {
            String[] pair = part.split("=",2);
            result.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }
        return result;
    }
}
