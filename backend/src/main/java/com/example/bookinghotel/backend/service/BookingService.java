package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.api.dto.BookingRequest;
import com.example.bookinghotel.backend.api.dto.BookingResponse;
import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.RoomEntity;
import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.exception.BadRequestException;
import com.example.bookinghotel.backend.exception.ConflictException;
import com.example.bookinghotel.backend.exception.NotFoundException;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.RoomJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BookingService {
    private final RoomJpaRepository roomRepository;
    private final BookingJpaRepository bookingRepository;
    private final CurrentUserService currentUserService;

    public BookingService(
            RoomJpaRepository roomRepository,
            BookingJpaRepository bookingRepository,
            CurrentUserService currentUserService
    ) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        UserEntity user = currentUserService.requireUser();
        LocalDate checkIn = toDate(request.checkInDate());
        LocalDate checkOut = toDate(request.checkOutDate());
        long nightsLong = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nightsLong <= 0 || nightsLong > Integer.MAX_VALUE) {
            throw new BadRequestException("INVALID_DATES", "Check-out must be after check-in");
        }

        // PESSIMISTIC_WRITE locks this room row until the transaction commits. Concurrent
        // requests are serialized and the second request sees the already-updated inventory.
        RoomEntity room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Room not found"));

        if (request.quantity() > room.getAvailableRooms()) {
            throw new ConflictException("ROOM_UNAVAILABLE", "Not enough rooms available");
        }

        int nights = (int) nightsLong;
        BigDecimal totalPrice = room.getPricePerNight()
                .multiply(BigDecimal.valueOf(request.quantity()))
                .multiply(BigDecimal.valueOf(nights));

        room.setAvailableRooms(room.getAvailableRooms() - request.quantity());

        BookingEntity booking = bookingRepository.save(new BookingEntity(
                room,
                user,
                request.quantity(),
                checkIn,
                checkOut,
                request.guests(),
                nights,
                totalPrice,
                BookingStatus.PENDING_PAYMENT,
                Instant.now()
        ));

        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(int bookingId) {
        return toResponse(findOwnedBooking(bookingId));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings() {
        Long userId = currentUserService.requireUser().getId();
        return bookingRepository.findAllByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(BookingService::toResponse)
                .toList();
    }

    public BookingEntity findOwnedBooking(int bookingId) {
        Long userId = currentUserService.requireUser().getId();
        return bookingRepository.findByIdAndUser_Id(bookingId, userId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
    }

    public static BookingResponse toResponse(BookingEntity booking) {
        return new BookingResponse(
                booking.getId(),
                RoomService.toResponse(booking.getRoom()),
                booking.getQuantity(),
                booking.getTotalPrice().doubleValue(),
                booking.getStatus().name(),
                toEpochMillis(booking.getCheckInDate()),
                toEpochMillis(booking.getCheckOutDate()),
                booking.getGuests(),
                booking.getNights(),
                booking.getCreatedAt().toEpochMilli()
        );
    }

    private static LocalDate toDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static long toEpochMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
