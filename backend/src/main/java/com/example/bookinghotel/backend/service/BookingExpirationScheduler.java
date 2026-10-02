package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BookingExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(BookingExpirationScheduler.class);

    private final BookingJpaRepository bookingRepository;
    private final BookingExpirationService expirationService;

    public BookingExpirationScheduler(
            BookingJpaRepository bookingRepository,
            BookingExpirationService expirationService
    ) {
        this.bookingRepository = bookingRepository;
        this.expirationService = expirationService;
    }

    @Scheduled(fixedDelayString = "${booking.expiration-scan-ms:60000}")
    public void expireStaleReservations() {
        Instant now = Instant.now();
        for (Integer bookingId : bookingRepository.findExpiredReservationIds(now)) {
            try {
                expirationService.expireIfDue(bookingId, now);
            } catch (RuntimeException exception) {
                // One bad row must not stop expiration of the remaining reservations.
                log.warn("Failed to expire booking {}: {}", bookingId, exception.getMessage());
            }
        }
    }
}
