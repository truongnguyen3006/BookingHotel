package com.example.bookinghotel.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
public class BookingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    private int guests;

    @Column(nullable = false)
    private int nights;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BookingEntity() {}

    public BookingEntity(RoomEntity room, UserEntity user, int quantity, LocalDate checkInDate, LocalDate checkOutDate,
                         int guests, int nights, BigDecimal totalPrice, BookingStatus status, Instant createdAt) {
        this.room = room;
        this.user = user;
        this.quantity = quantity;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.guests = guests;
        this.nights = nights;
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Kept for older unit tests / legacy rows created before authentication.
    public BookingEntity(RoomEntity room, int quantity, LocalDate checkInDate, LocalDate checkOutDate,
                         int guests, int nights, BigDecimal totalPrice, BookingStatus status, Instant createdAt) {
        this(room, null, quantity, checkInDate, checkOutDate, guests, nights, totalPrice, status, createdAt);
    }

    public Integer getId() { return id; }
    public RoomEntity getRoom() { return room; }
    public UserEntity getUser() { return user; }
    public int getQuantity() { return quantity; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public int getGuests() { return guests; }
    public int getNights() { return nights; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(BookingStatus status) { this.status = status; }
}
