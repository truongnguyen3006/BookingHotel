package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.api.dto.AdminBookingResponse;
import com.example.bookinghotel.backend.api.dto.AdminDashboardResponse;
import com.example.bookinghotel.backend.api.dto.AdminPaymentResponse;
import com.example.bookinghotel.backend.api.dto.AdminRoomUpdateRequest;
import com.example.bookinghotel.backend.api.dto.RoomResponse;
import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.RoomEntity;
import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.exception.NotFoundException;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.PaymentJpaRepository;
import com.example.bookinghotel.backend.repository.RoomJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminService {
    private final RoomJpaRepository roomRepository;
    private final BookingJpaRepository bookingRepository;
    private final PaymentJpaRepository paymentRepository;

    public AdminService(
            RoomJpaRepository roomRepository,
            BookingJpaRepository bookingRepository,
            PaymentJpaRepository paymentRepository
    ) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAll().stream()
                .sorted(Comparator.comparing(RoomEntity::getId))
                .map(RoomService::toResponse)
                .toList();
    }

    @Transactional
    public RoomResponse updateRoom(int roomId, AdminRoomUpdateRequest request) {
        // Use the same row lock as booking creation so an admin inventory edit cannot race
        // with a customer taking the last room.
        RoomEntity room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Room not found"));

        if (request.pricePerNight() != null) {
            room.setPricePerNight(request.pricePerNight());
        }
        if (request.availableRooms() != null) {
            room.setAvailableRooms(request.availableRooms());
        }
        return RoomService.toResponse(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<AdminBookingResponse> getBookings() {
        return bookingRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminBookingResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        List<BookingEntity> bookings = bookingRepository.findAll();
        List<RoomEntity> rooms = roomRepository.findAll();

        long paidBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.SUCCESS)
                .count();
        long totalRevenue = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.SUCCESS)
                .mapToLong(BookingEntity::getTotalPrice)
                .reduce(0L, Math::addExact);
        long availableInventory = rooms.stream()
                .mapToLong(RoomEntity::getAvailableRooms)
                .sum();

        return new AdminDashboardResponse(
                bookings.size(),
                paidBookings,
                totalRevenue,
                rooms.size(),
                availableInventory
        );
    }

    private AdminBookingResponse toAdminBookingResponse(BookingEntity booking) {
        UserEntity user = booking.getUser();
        PaymentEntity payment = paymentRepository
                .findFirstByBooking_IdOrderByCreatedAtDesc(booking.getId())
                .orElse(null);

        return new AdminBookingResponse(
                booking.getId(),
                RoomService.toResponse(booking.getRoom()),
                booking.getQuantity(),
                booking.getTotalPrice(),
                booking.getStatus().name(),
                booking.getCheckInDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                booking.getCheckOutDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                booking.getGuests(),
                booking.getNights(),
                booking.getCreatedAt().toEpochMilli(),
                user == null ? null : user.getId(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getDisplayName(),
                payment == null ? null : new AdminPaymentResponse(
                        payment.getStatus().name(),
                        payment.getMethod().name(),
                        payment.getTransactionId(),
                        payment.getPaidAt() == null ? null : payment.getPaidAt().toEpochMilli()
                )
        );
    }
}
