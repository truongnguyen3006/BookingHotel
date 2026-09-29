package com.example.bookinghotel.data.local

import com.example.bookinghotel.data.Booking

fun BookingEntity.toDomain(): Booking {
    return Booking(
        localId = localId,
        bookingId = remoteBookingId,
        roomId = roomId,
        roomTypeKey = roomTypeKey,
        quantity = quantity,
        pricePerNight = pricePerNight,
        totalPrice = totalPrice,
        status = status,
        createdAt = createdAt
    )
}

fun Booking.toEntity(): BookingEntity {
    return BookingEntity(
        localId = localId,
        remoteBookingId = bookingId,
        roomId = roomId,
        roomTypeKey = roomTypeKey,
        quantity = quantity,
        pricePerNight = pricePerNight,
        totalPrice = totalPrice,
        status = status,
        createdAt = createdAt
    )
}
