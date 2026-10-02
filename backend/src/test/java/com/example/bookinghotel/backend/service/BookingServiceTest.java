package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.api.dto.BookingRequest;
import com.example.bookinghotel.backend.domain.Role;
import com.example.bookinghotel.backend.domain.RoomEntity;
import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.exception.ConflictException;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.RoomJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    @Test
    void rejectsQuantityAboveAvailableInventory() {
        RoomJpaRepository roomRepository = mock(RoomJpaRepository.class);
        BookingJpaRepository bookingRepository = mock(BookingJpaRepository.class);
        RoomEntity room = new RoomEntity(
                "standard_room",
                "standard",
                1_250_000L,
                new LinkedHashSet<>(),
                1
        );
        when(roomRepository.findByIdForUpdate(1)).thenReturn(Optional.of(room));
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireUser()).thenReturn(new UserEntity(
                "test@example.com", "hash", "Test User", Role.USER, true, java.time.Instant.now()
        ));

        BookingService service = new BookingService(roomRepository, bookingRepository, currentUserService);
        long checkIn = LocalDate.of(2026, 10, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long checkOut = LocalDate.of(2026, 10, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        assertThrows(ConflictException.class, () -> service.createBooking(
                new BookingRequest(1, 2, checkIn, checkOut, 2)
        ));
    }
}
