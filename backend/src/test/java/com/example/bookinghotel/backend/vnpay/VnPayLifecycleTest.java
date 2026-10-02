package com.example.bookinghotel.backend.vnpay;

import com.example.bookinghotel.backend.config.VnPayProperties;
import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.PaymentStatus;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.PaymentJpaRepository;
import com.example.bookinghotel.backend.service.BookingLifecycleService;
import com.example.bookinghotel.backend.service.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnPayLifecycleTest {
    private static final String SECRET = "sandbox-secret-for-lifecycle-test";
    private static final String TMN_CODE = "B0X8KC9I";
    private static final String TXN_REF = "BH421700000000000ABCDEF";

    @Test
    void failedIpn_releasesInventoryAndMarksProviderPaymentFailed() {
        Fixture fixture = fixture(PaymentStatus.PENDING);
        Map<String, String> callback = signedCallback("24", "02");

        Map<String, String> result = fixture.service().processIpn(callback);

        assertEquals("00", result.get("RspCode"));
        verify(fixture.payment()).completeProviderPayment(
                PaymentStatus.FAILED,
                "99887766",
                "24",
                "02",
                null
        );
        verify(fixture.lifecycle()).markFailedAndRelease(fixture.booking());
        verify(fixture.lifecycle(), never()).markSuccessful(fixture.booking());
    }

    @Test
    void successfulIpn_keepsInventoryConsumedAndMarksBookingSuccessful() {
        Fixture fixture = fixture(PaymentStatus.PENDING);
        Map<String, String> callback = signedCallback("00", "00");

        Map<String, String> result = fixture.service().processIpn(callback);

        assertEquals("00", result.get("RspCode"));
        verify(fixture.payment()).completeProviderPayment(
                eq(PaymentStatus.SUCCESS),
                eq("99887766"),
                eq("00"),
                eq("00"),
                any(Instant.class)
        );
        verify(fixture.lifecycle()).markSuccessful(fixture.booking());
        verify(fixture.lifecycle(), never()).markFailedAndRelease(fixture.booking());
    }

    @Test
    void duplicateTerminalIpn_doesNotApplyInventoryTransitionAgain() {
        Fixture fixture = fixture(PaymentStatus.FAILED);
        Map<String, String> callback = signedCallback("24", "02");

        Map<String, String> result = fixture.service().processIpn(callback);

        assertEquals("02", result.get("RspCode"));
        verify(fixture.lifecycle(), never()).markFailedAndRelease(fixture.booking());
        verify(fixture.lifecycle(), never()).markSuccessful(fixture.booking());
    }

    @Test
    void lifecycleFailureEscapesTransactionalBoundaryInsteadOfCommittingPayment() {
        Fixture fixture = fixture(PaymentStatus.PENDING);
        org.mockito.Mockito.doThrow(new IllegalStateException("inventory failure"))
                .when(fixture.lifecycle()).markSuccessful(fixture.booking());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> fixture.service().processIpn(signedCallback("00", "00")));
    }

    @Test
    void successPaymentRejectsLaterFailedCallbackWithoutChangingInventory() {
        Fixture fixture = fixture(PaymentStatus.SUCCESS);
        assertEquals("02", fixture.service().processIpn(signedCallback("24", "02")).get("RspCode"));
        verify(fixture.lifecycle(), never()).markFailedAndRelease(fixture.booking());
    }

    @Test
    void pendingCallbackCannotResurrectReleasedBooking() {
        Fixture fixture = fixture(PaymentStatus.PENDING);
        when(fixture.booking().isInventoryReleased()).thenReturn(true);
        assertEquals("02", fixture.service().processIpn(signedCallback("00", "00")).get("RspCode"));
        verify(fixture.lifecycle(), never()).markSuccessful(fixture.booking());
        verify(fixture.payment()).completeProviderPayment(PaymentStatus.FAILED, null,
                "SUPERSEDED", "SUPERSEDED", null);
    }

    private Fixture fixture(PaymentStatus status) {
        BookingJpaRepository bookingRepository = mock(BookingJpaRepository.class);
        PaymentJpaRepository paymentRepository = mock(PaymentJpaRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        BookingLifecycleService lifecycle = mock(BookingLifecycleService.class);
        VnPayProperties properties = mock(VnPayProperties.class);
        BookingEntity discoveredBooking = mock(BookingEntity.class);
        BookingEntity lockedBooking = mock(BookingEntity.class);
        PaymentEntity payment = mock(PaymentEntity.class);

        when(properties.isConfigured()).thenReturn(true);
        when(properties.getHashSecret()).thenReturn(SECRET);
        when(properties.getTmnCode()).thenReturn(TMN_CODE);

        when(discoveredBooking.getId()).thenReturn(42);
        when(payment.getBooking()).thenReturn(discoveredBooking);
        when(payment.getAmountVnd()).thenReturn(1_250_000L);
        when(payment.getStatus()).thenReturn(status);

        when(paymentRepository.findBookingIdByProviderReference(TXN_REF)).thenReturn(Optional.of(42));
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(lockedBooking));
        when(paymentRepository.findByProviderReferenceForUpdate(TXN_REF)).thenReturn(Optional.of(payment));

        VnPayService service = new VnPayService(
                bookingRepository,
                paymentRepository,
                currentUserService,
                lifecycle,
                properties
        );
        return new Fixture(service, lifecycle, lockedBooking, payment);
    }

    private Map<String, String> signedCallback(String responseCode, String transactionStatus) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TxnRef", TXN_REF);
        params.put("vnp_Amount", "125000000");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TransactionNo", "99887766");
        params.put("vnp_PayDate", "20261002123000");
        return parseQuery(VnPaySigner.buildSignedQuery(params, SECRET));
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length > 1
                    ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                    : "";
            result.put(key, value);
        }
        return result;
    }

    private record Fixture(
            VnPayService service,
            BookingLifecycleService lifecycle,
            BookingEntity booking,
            PaymentEntity payment
    ) {}
}
