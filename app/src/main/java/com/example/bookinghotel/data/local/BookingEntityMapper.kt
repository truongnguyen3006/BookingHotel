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
        createdAt = createdAt,
        checkInDate = checkInDate,
        checkOutDate = checkOutDate,
        guests = guests,
        nights = nights,
        paymentMethod = paymentMethod,
        transactionId = transactionId,
        paidAt = paidAt
    )
}

fun Booking.toEntity(ownerSessionId: String = ""): BookingEntity {
    return BookingEntity(
        ownerSessionId = ownerSessionId,
        localId = localId,
        remoteBookingId = bookingId,
        roomId = roomId,
        roomTypeKey = roomTypeKey,
        quantity = quantity,
        pricePerNight = pricePerNight,
        totalPrice = totalPrice,
        status = status,
        createdAt = createdAt,
        checkInDate = checkInDate,
        checkOutDate = checkOutDate,
        guests = guests,
        nights = nights,
        paymentMethod = paymentMethod,
        transactionId = transactionId,
        paidAt = paidAt
    )
}
