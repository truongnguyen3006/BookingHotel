package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.RoomEntity;
import com.example.bookinghotel.backend.exception.ConflictException;
import com.example.bookinghotel.backend.exception.NotFoundException;
import com.example.bookinghotel.backend.repository.RoomJpaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Centralizes booking reservation state transitions.
 *
 * Invariant:
 * - PENDING_PAYMENT / PROCESSING / SUCCESS -> the booking quantity is reserved/consumed.
 * - FAILED -> the booking quantity has already been returned to room inventory.
 *
 * Callers hold a pessimistic lock on the booking row before invoking this service.
 */
@Service
public class BookingLifecycleService {
    private final RoomJpaRepository roomRepository;

    public BookingLifecycleService(RoomJpaRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * A FAILED booking has already released inventory. A retry must reserve it again
     * atomically before another payment attempt can start.
     */
    public void ensureInventoryReservedForRetry(BookingEntity booking, Instant reservationExpiresAt) {
        if (booking.getStatus() == BookingStatus.SUCCESS) {
            throw new ConflictException("BOOKING_ALREADY_PAID", "Booking already paid");
        }

        if (booking.isInventoryReleased()) {
            RoomEntity room = lockRoom(booking);
            if (booking.getQuantity() > room.getAvailableRooms()) {
                throw new ConflictException(
                        "ROOM_UNAVAILABLE",
                        "Not enough rooms available to retry this booking"
                );
            }
            room.setAvailableRooms(room.getAvailableRooms() - booking.getQuantity());
            booking.setInventoryReleased(false);
        }

        if (booking.getStatus() == BookingStatus.FAILED) {
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
        }

        if (reservationExpiresAt != null) {
            booking.setReservationExpiresAt(reservationExpiresAt);
        }
    }

    /** Moves a reserved booking into payment processing. */
    public void markProcessing(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.SUCCESS) {
            throw new ConflictException("BOOKING_ALREADY_PAID", "Booking already paid");
        }
        if (booking.isInventoryReleased()) {
            throw new IllegalStateException("Cannot process a booking whose inventory is not reserved");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT
                && booking.getStatus() != BookingStatus.PROCESSING) {
            throw new IllegalStateException("Invalid booking transition to PROCESSING from " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.PROCESSING);
    }

    /** Marks the booking paid. Reserved inventory stays consumed. */
    public void markSuccessful(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.SUCCESS) {
            return;
        }
        if (booking.isInventoryReleased()) {
            throw new IllegalStateException("Cannot mark booking SUCCESS after inventory was released");
        }
        if (booking.getStatus() != BookingStatus.PROCESSING
                && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Invalid booking transition to SUCCESS from " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.SUCCESS);
        booking.setReservationExpiresAt(null);
    }

    /**
     * Terminal failure transition. Inventory is returned exactly once because FAILED
     * is the invariant that inventory has already been released.
     */
    public void markFailedAndRelease(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.SUCCESS) {
            return;
        }

        if (!booking.isInventoryReleased()) {
            RoomEntity room = lockRoom(booking);
            room.setAvailableRooms(Math.addExact(room.getAvailableRooms(), booking.getQuantity()));
            booking.setInventoryReleased(true);
        }
        booking.setStatus(BookingStatus.FAILED);
        booking.setReservationExpiresAt(null);
    }

    private RoomEntity lockRoom(BookingEntity booking) {
        return roomRepository.findByIdForUpdate(booking.getRoom().getId())
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Room not found"));
    }
}
