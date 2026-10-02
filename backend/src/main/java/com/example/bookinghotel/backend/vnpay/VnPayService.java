package com.example.bookinghotel.backend.vnpay;

import com.example.bookinghotel.backend.api.dto.VnPayCreateResponse;
import com.example.bookinghotel.backend.api.dto.VnPayStatusResponse;
import com.example.bookinghotel.backend.config.VnPayProperties;
import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.PaymentMethod;
import com.example.bookinghotel.backend.domain.PaymentStatus;
import com.example.bookinghotel.backend.exception.BadRequestException;
import com.example.bookinghotel.backend.exception.ConflictException;
import com.example.bookinghotel.backend.exception.NotFoundException;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.PaymentJpaRepository;
import com.example.bookinghotel.backend.service.BookingLifecycleService;
import com.example.bookinghotel.backend.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VnPayService {
    private static final Logger log = LoggerFactory.getLogger(VnPayService.class);
    private static final Pattern IPV4_PATTERN = Pattern.compile("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])");
    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long MAX_VNPAY_AMOUNT_VND = 9_999_999_999L;

    private final BookingJpaRepository bookingRepository;
    private final PaymentJpaRepository paymentRepository;
    private final CurrentUserService currentUserService;
    private final BookingLifecycleService bookingLifecycleService;
    private final VnPayProperties properties;

    public VnPayService(
            BookingJpaRepository bookingRepository,
            PaymentJpaRepository paymentRepository,
            CurrentUserService currentUserService,
            BookingLifecycleService bookingLifecycleService,
            VnPayProperties properties
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserService = currentUserService;
        this.bookingLifecycleService = bookingLifecycleService;
        this.properties = properties;
    }

    @Transactional
    public VnPayCreateResponse createPayment(int bookingId, String idempotencyKey, String clientIp) {
        requireConfiguration();
        Long userId = currentUserService.requireUser().getId();

        // Serialize payment creation per booking. This makes two devices / rapid retries
        // converge on one active VNPAY transaction instead of creating parallel charges.
        BookingEntity booking = bookingRepository.findOwnedByIdForUpdate(bookingId, userId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));

        if (booking.getStatus() == BookingStatus.SUCCESS) {
            throw new ConflictException("BOOKING_ALREADY_PAID", "Booking already paid");
        }

        Instant now = Instant.now();
        var idempotent = paymentRepository.findByBooking_IdAndIdempotencyKey(bookingId, idempotencyKey);
        if (idempotent.isPresent()) {
            PaymentEntity existing = idempotent.get();
            if (existing.getMethod() != PaymentMethod.VNPAY || existing.getProviderReference() == null) {
                throw new ConflictException("PAYMENT_KEY_REUSED", "Idempotency key already belongs to another payment attempt");
            }
            if (existing.getStatus() == PaymentStatus.PENDING && isConfirmationTimedOut(existing, now)) {
                expirePayment(existing, booking);
                return toClosedCreateResponse(existing);
            }
            if (existing.getStatus() != PaymentStatus.PENDING || isPaymentUrlExpired(existing, now)) {
                return toClosedCreateResponse(existing);
            }
            return toCreateResponse(existing, clientIp);
        }

        PaymentEntity latest = paymentRepository.findLatestByBookingIdForUpdate(bookingId).orElse(null);
        if (latest != null && latest.getMethod() == PaymentMethod.VNPAY && latest.getStatus() == PaymentStatus.PENDING) {
            if (isConfirmationTimedOut(latest, now)) {
                expirePayment(latest, booking);
            } else if (isPaymentUrlExpired(latest, now)) {
                // The provider URL is closed, but keep the reservation during the short
                // confirmation grace period so a delayed IPN cannot oversell inventory.
                return toClosedCreateResponse(latest);
            } else {
                // A different idempotency key may come from another device/restarted app.
                // Reuse the one still-active provider transaction.
                return toCreateResponse(latest, clientIp);
            }
        }

        long amountVnd = validateVndAmount(booking.getTotalPrice());
        String providerReference = newProviderReference(bookingId, now);
        Instant settlementDeadline = now
                .plusSeconds(properties.getExpireMinutes() * 60L)
                .plusSeconds(properties.getConfirmationGraceSeconds());
        bookingLifecycleService.ensureInventoryReservedForRetry(booking, settlementDeadline);

        bookingLifecycleService.markProcessing(booking);
        PaymentEntity payment = paymentRepository.save(new PaymentEntity(
                booking,
                PaymentMethod.VNPAY,
                PaymentStatus.PENDING,
                null,
                idempotencyKey,
                providerReference,
                null,
                null,
                amountVnd,
                null,
                now
        ));

        return toCreateResponse(payment, clientIp);
    }

    @Transactional
    public VnPayStatusResponse getStatus(int bookingId) {
        Long userId = currentUserService.requireUser().getId();
        BookingEntity booking = bookingRepository.findOwnedByIdForUpdate(bookingId, userId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));

        PaymentEntity payment = paymentRepository.findLatestByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "Payment not found"));

        if (payment.getMethod() != PaymentMethod.VNPAY) {
            throw new BadRequestException("PAYMENT_NOT_VNPAY", "Latest payment is not a VNPAY payment");
        }
        if (payment.getStatus() == PaymentStatus.PENDING && isConfirmationTimedOut(payment, Instant.now())) {
            expirePayment(payment, booking);
        }
        return toStatusResponse(payment);
    }

    @Transactional
    public Map<String, String> processIpn(Map<String, String> params) {
        if (!properties.isConfigured()) {
            return ipn("99", "VNPAY is not configured");
        }
        try {
            if (!VnPaySigner.verify(params, properties.getHashSecret())) {
                return ipn("97", "Invalid signature");
            }
            if (!Objects.equals(properties.getTmnCode(), params.get("vnp_TmnCode"))) {
                return ipn("97", "Invalid TmnCode");
            }

            String txnRef = params.get("vnp_TxnRef");
            if (txnRef == null || txnRef.isBlank()) {
                return ipn("99", "Invalid request");
            }

            PaymentEntity discovered = paymentRepository.findByProviderReferenceWithBooking(txnRef).orElse(null);
            if (discovered == null) {
                return ipn("01", "Order not found");
            }

            // Keep lock ordering consistent with user-initiated payment creation:
            // booking first, then payment. This avoids a booking/payment lock inversion.
            BookingEntity booking = bookingRepository.findByIdForUpdate(discovered.getBooking().getId())
                    .orElseThrow(() -> new IllegalStateException("Booking disappeared during VNPAY IPN"));
            PaymentEntity payment = paymentRepository.findByProviderReferenceForUpdate(txnRef)
                    .orElseThrow(() -> new IllegalStateException("Payment disappeared during VNPAY IPN"));

            long responseAmountVnd = parseVnpAmount(params.get("vnp_Amount"));
            if (payment.getAmountVnd() == null || responseAmountVnd != payment.getAmountVnd()) {
                return ipn("04", "Invalid amount");
            }

            if (payment.getStatus() != PaymentStatus.PENDING) {
                return ipn("02", "Order already confirmed");
            }

            String responseCode = valueOrEmpty(params.get("vnp_ResponseCode"));
            String transactionStatus = valueOrEmpty(params.get("vnp_TransactionStatus"));
            String transactionNo = blankToNull(params.get("vnp_TransactionNo"));
            boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);
            Instant paidAt = success ? parsePayDate(params.get("vnp_PayDate")) : null;

            payment.completeProviderPayment(
                    success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED,
                    transactionNo,
                    responseCode,
                    transactionStatus,
                    paidAt
            );
            if (success) {
                bookingLifecycleService.markSuccessful(booking);
            } else {
                bookingLifecycleService.markFailedAndRelease(booking);
            }

            return ipn("00", "Confirm Success");
        } catch (RuntimeException exception) {
            log.warn("VNPAY IPN processing failed: {}", exception.getMessage());
            return ipn("99", "Unknown error");
        }
    }

    public boolean isValidReturn(Map<String, String> params) {
        return properties.isConfigured()
                && Objects.equals(properties.getTmnCode(), params.get("vnp_TmnCode"))
                && VnPaySigner.verify(params, properties.getHashSecret());
    }

    @Transactional(readOnly = true)
    public String returnDeepLink(Map<String, String> params) {
        boolean valid = isValidReturn(params);
        String txnRef = valueOrEmpty(params.get("vnp_TxnRef"));
        PaymentEntity payment = txnRef.isBlank()
                ? null
                : paymentRepository.findByProviderReference(txnRef).orElse(null);
        int bookingId = payment == null ? 0 : payment.getBooking().getId();
        String responseCode = valid ? valueOrEmpty(params.get("vnp_ResponseCode")) : "97";
        return "bookinghotel://payment-result?bookingId=" + bookingId
                + "&responseCode=" + safeDeepLinkValue(responseCode)
                + "&txnRef=" + safeDeepLinkValue(txnRef);
    }

    public String returnMessage(Map<String, String> params) {
        if (!isValidReturn(params)) {
            return "Chu ky VNPAY khong hop le.";
        }
        return responseMessage(params.get("vnp_ResponseCode"));
    }

    private VnPayCreateResponse toCreateResponse(PaymentEntity payment, String clientIp) {
        Instant createdAt = payment.getCreatedAt();
        Instant expiresAt = paymentUrlExpiresAt(payment);
        Map<String, String> params = buildPaymentParams(payment, clientIp, createdAt, expiresAt);
        log.info(
                "VNPAY create bookingId={} txnRef={} tmnCode={} amount={} ip={} createDate={} expireDate={} returnUrl={} hashSecretLength={}",
                payment.getBooking().getId(),
                payment.getProviderReference(),
                properties.getTmnCode(),
                params.get("vnp_Amount"),
                params.get("vnp_IpAddr"),
                params.get("vnp_CreateDate"),
                params.get("vnp_ExpireDate"),
                properties.getReturnUrl(),
                properties.getHashSecret().length()
        );
        String query = VnPaySigner.buildSignedQuery(params, properties.getHashSecret());
        String paymentUrl = properties.getPayUrl() + "?" + query;
        return new VnPayCreateResponse(
                payment.getBooking().getId(),
                payment.getStatus().name(),
                paymentUrl,
                payment.getProviderReference(),
                payment.getAmountVnd() == null ? 0L : payment.getAmountVnd(),
                expiresAt.toEpochMilli()
        );
    }

    private VnPayCreateResponse toClosedCreateResponse(PaymentEntity payment) {
        return new VnPayCreateResponse(
                payment.getBooking().getId(),
                payment.getStatus().name(),
                "",
                payment.getProviderReference(),
                payment.getAmountVnd() == null ? 0L : payment.getAmountVnd(),
                paymentUrlExpiresAt(payment).toEpochMilli()
        );
    }

    private Map<String, String> buildPaymentParams(
            PaymentEntity payment,
            String clientIp,
            Instant createdAt,
            Instant expiresAt
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(Math.multiplyExact(payment.getAmountVnd(), 100L)));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getProviderReference());
        params.put("vnp_OrderInfo", "Thanh toan BookingHotel booking " + payment.getBooking().getId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", formatVnPayTime(createdAt));
        params.put("vnp_ExpireDate", formatVnPayTime(expiresAt));
        return params;
    }

    private VnPayStatusResponse toStatusResponse(PaymentEntity payment) {
        String message = switch (payment.getStatus()) {
            case PENDING -> "Waiting for VNPAY confirmation";
            case SUCCESS -> "Payment completed successfully";
            case FAILED -> responseMessage(payment.getProviderResponseCode());
        };
        return new VnPayStatusResponse(
                payment.getBooking().getId(),
                payment.getStatus().name(),
                payment.getMethod().name(),
                payment.getTransactionId(),
                payment.getProviderReference(),
                payment.getAmountVnd() == null ? 0L : payment.getAmountVnd(),
                payment.getProviderResponseCode(),
                message,
                payment.getPaidAt() == null ? null : payment.getPaidAt().toEpochMilli()
        );
    }

    private long validateVndAmount(long amountVnd) {
        // Booking.totalPrice is already a native VND snapshot taken at booking creation.
        // Retries therefore cannot drift when room prices are edited later.
        if (amountVnd <= 0 || amountVnd > MAX_VNPAY_AMOUNT_VND) {
            throw new BadRequestException("VNPAY_INVALID_AMOUNT", "Payment amount is outside VNPAY limits");
        }
        return amountVnd;
    }

    private long parseVnpAmount(String raw) {
        if (raw == null || raw.isBlank()) return -1L;
        try {
            long multiplied = Long.parseLong(raw);
            if (multiplied < 0 || multiplied % 100L != 0L) return -1L;
            return multiplied / 100L;
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }


    private Instant parsePayDate(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return LocalDateTime.parse(raw, VNPAY_TIME)
                    .atZone(VNPAY_ZONE)
                    .toInstant();
        } catch (RuntimeException exception) {
            return Instant.now();
        }
    }

    private boolean isPaymentUrlExpired(PaymentEntity payment, Instant now) {
        return !now.isBefore(paymentUrlExpiresAt(payment));
    }

    private boolean isConfirmationTimedOut(PaymentEntity payment, Instant now) {
        return !now.isBefore(settlementDeadline(payment));
    }

    private Instant paymentUrlExpiresAt(PaymentEntity payment) {
        return payment.getCreatedAt().plusSeconds(properties.getExpireMinutes() * 60L);
    }

    private Instant settlementDeadline(PaymentEntity payment) {
        return paymentUrlExpiresAt(payment).plusSeconds(properties.getConfirmationGraceSeconds());
    }

    private void expirePayment(PaymentEntity payment, BookingEntity booking) {
        if (payment.getStatus() != PaymentStatus.PENDING) return;
        payment.completeProviderPayment(
                PaymentStatus.FAILED,
                payment.getTransactionId(),
                "EXPIRED",
                "EXPIRED",
                null
        );
        bookingLifecycleService.markFailedAndRelease(booking);
    }

    private String newProviderReference(int bookingId, Instant now) {
        String random = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return "BH" + bookingId + now.toEpochMilli() + random;
    }

    private void requireConfiguration() {
        if (!properties.getTmnCode().matches("[A-Za-z0-9]{8}")) {
            throw new IllegalStateException("VNP_TMN_CODE must contain exactly 8 alphanumeric characters.");
        }
        if (properties.getHashSecret().isBlank()) {
            throw new IllegalStateException("VNP_HASH_SECRET is missing.");
        }
        if (!properties.isConfigured()) {
            throw new IllegalStateException("VNPAY configuration is incomplete. Check VNP_PAY_URL and VNP_RETURN_URL.");
        }
        if (properties.getExpireMinutes() <= 0 || properties.getConfirmationGraceSeconds() < 0) {
            throw new IllegalStateException("VNPAY expiration configuration is invalid");
        }
    }

    private String formatVnPayTime(Instant instant) {
        return VNPAY_TIME.format(ZonedDateTime.ofInstant(instant, VNPAY_ZONE));
    }

    private String normalizeIp(String raw) {
        // VNPAY documents vnp_IpAddr using IPv4 examples. Railway may forward an IPv6
        // address, so prefer a valid IPv4 address and use the documented localhost
        // fallback for sandbox requests when no IPv4 address is available.
        if (raw == null || raw.isBlank()) return "127.0.0.1";
        String first = raw.split(",")[0].trim();
        Matcher matcher = IPV4_PATTERN.matcher(first);
        if (matcher.find() && isValidIpv4(matcher.group())) {
            return matcher.group();
        }
        log.warn("VNPAY request received non-IPv4 client address; using sandbox fallback 127.0.0.1");
        return "127.0.0.1";
    }

    private boolean isValidIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255) return false;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> ipn(String code, String message) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("RspCode", code);
        result.put("Message", message);
        return result;
    }

    private String responseMessage(String code) {
        if (code == null || code.isBlank()) return "Payment failed";
        return switch (code) {
            case "00" -> "Thanh toan thanh cong";
            case "07" -> "Giao dich bi nghi ngo bat thuong";
            case "09" -> "The/tai khoan chua dang ky Internet Banking";
            case "10" -> "Xac thuc thong tin the/tai khoan khong dung";
            case "11" -> "Da het han cho thanh toan";
            case "12" -> "The/tai khoan bi khoa";
            case "13" -> "Sai OTP";
            case "24" -> "Khach hang huy giao dich";
            case "51" -> "Tai khoan khong du so du";
            case "65" -> "Vuot han muc giao dich trong ngay";
            case "75" -> "Ngan hang dang bao tri";
            case "79" -> "Nhap sai mat khau thanh toan qua so lan quy dinh";
            case "EXPIRED" -> "Phien thanh toan da het han";
            default -> "Thanh toan khong thanh cong (ma " + code + ")";
        };
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safeDeepLinkValue(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9]", "");
    }
}
