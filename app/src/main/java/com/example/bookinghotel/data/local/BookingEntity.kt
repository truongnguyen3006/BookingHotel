package com.example.bookinghotel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteBookingId: Int,
    val roomId: Int,
    val roomTypeKey: String,
    val quantity: Int,
    val pricePerNight: Double,
    val totalPrice: Double,
    val status: String,
    val createdAt: Long
)
