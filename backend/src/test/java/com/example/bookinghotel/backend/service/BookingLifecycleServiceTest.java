package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.RoomEntity;
import com.example.bookinghotel.backend.repository.RoomJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingLifecycleServiceTest {

    @Test
    void failedBooking_releasesInventoryExactlyOnce() {
        RoomJpaRepository roomRepository = mock(RoomJpaRepository.class);
        RoomEntity room = mock(RoomEntity.class);
        BookingEntity booking = mock(BookingEntity.class);
        AtomicInteger available = new AtomicInteger(4);
        AtomicBoolean released = new AtomicBoolean(false);
        AtomicReference<BookingStatus> status = new AtomicReference<>(BookingStatus.PROCESSING);

        when(room.getId()).thenReturn(1);
        when(room.getAvailableRooms()).thenAnswer(invocation -> available.get());
        doAnswer(invocation -> {
            available.set(invocation.getArgument(0));
            return null;
        }).when(room).setAvailableRooms(anyInt());

        when(booking.getRoom()).thenReturn(room);
        when(booking.getQuantity()).thenReturn(2);
        when(booking.getStatus()).thenAnswer(invocation -> status.get());
        doAnswer(invocation -> {
            status.set(invocation.getArgument(0));
            return null;
        }).when(booking).setStatus(org.mockito.ArgumentMatchers.any(BookingStatus.class));
        when(booking.isInventoryReleased()).thenAnswer(invocation -> released.get());
        doAnswer(invocation -> {
            released.set(invocation.getArgument(0));
            return null;
        }).when(booking).setInventoryReleased(anyBoolean());
        when(roomRepository.findByIdForUpdate(1)).thenReturn(Optional.of(room));

        BookingLifecycleService service = new BookingLifecycleService(roomRepository);
        service.markFailedAndRelease(booking);
        service.markFailedAndRelease(booking);

        assertEquals(6, available.get());
        assertEquals(BookingStatus.FAILED, status.get());
        verify(room, times(1)).setAvailableRooms(6);
        verify(booking, times(1)).setInventoryReleased(true);
    }

    @Test
    void retryAfterRelease_reReservesInventoryAndExtendsExpiry() {
        RoomJpaRepository roomRepository = mock(RoomJpaRepository.class);
        RoomEntity room = mock(RoomEntity.class);
        BookingEntity booking = mock(BookingEntity.class);
        AtomicInteger available = new AtomicInteger(3);
        AtomicBoolean released = new AtomicBoolean(true);
        AtomicReference<BookingStatus> status = new AtomicReference<>(BookingStatus.FAILED);

        when(room.getId()).thenReturn(1);
        when(room.getAvailableRooms()).thenAnswer(invocation -> available.get());
        doAnswer(invocation -> {
            available.set(invocation.getArgument(0));
            return null;
        }).when(room).setAvailableRooms(anyInt());

        when(booking.getRoom()).thenReturn(room);
        when(booking.getQuantity()).thenReturn(2);
        when(booking.getStatus()).thenAnswer(invocation -> status.get());
        doAnswer(invocation -> {
            status.set(invocation.getArgument(0));
            return null;
        }).when(booking).setStatus(org.mockito.ArgumentMatchers.any(BookingStatus.class));
        when(booking.isInventoryReleased()).thenAnswer(invocation -> released.get());
        doAnswer(invocation -> {
            released.set(invocation.getArgument(0));
            return null;
        }).when(booking).setInventoryReleased(anyBoolean());
        when(roomRepository.findByIdForUpdate(1)).thenReturn(Optional.of(room));

        BookingLifecycleService service = new BookingLifecycleService(roomRepository);
        Instant newExpiry = Instant.now().plusSeconds(900);
        service.ensureInventoryReservedForRetry(booking, newExpiry);

        assertEquals(1, available.get());
        assertEquals(false, released.get());
        assertEquals(BookingStatus.PENDING_PAYMENT, status.get());
        verify(booking).setReservationExpiresAt(newExpiry);
    }
    @Test
    void retryFailsWhenInventoryWasReleasedAndNoRoomsRemain() {
        RoomJpaRepository roomRepository = mock(RoomJpaRepository.class);
        RoomEntity room = mock(RoomEntity.class);
        BookingEntity booking = mock(BookingEntity.class);

        when(room.getId()).thenReturn(1);
        when(room.getAvailableRooms()).thenReturn(1);
        when(booking.getRoom()).thenReturn(room);
        when(booking.getQuantity()).thenReturn(2);
        when(booking.getStatus()).thenReturn(BookingStatus.FAILED);
        when(booking.isInventoryReleased()).thenReturn(true);
        when(roomRepository.findByIdForUpdate(1)).thenReturn(Optional.of(room));

        BookingLifecycleService service = new BookingLifecycleService(roomRepository);

        assertThrows(
                com.example.bookinghotel.backend.exception.ConflictException.class,
                () -> service.ensureInventoryReservedForRetry(booking, Instant.now().plusSeconds(900))
        );
    }

    @Test
    void releasedInventoryCannotTransitionDirectlyToSuccess() {
        RoomJpaRepository roomRepository = mock(RoomJpaRepository.class);
        BookingEntity booking = mock(BookingEntity.class);

        when(booking.getStatus()).thenReturn(BookingStatus.PROCESSING);
        when(booking.isInventoryReleased()).thenReturn(true);

        BookingLifecycleService service = new BookingLifecycleService(roomRepository);

        assertThrows(IllegalStateException.class, () -> service.markSuccessful(booking));
    }

}
