package com.example.bookinghotel.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteBookingId: Int,
    val roomId: Int,
    val roomTypeKey: String,
    val quantity: Int,
    val pricePerNight: Long,
    val totalPrice: Long,
    val status: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val checkInDate: Long = 0L,
    @ColumnInfo(defaultValue = "0") val checkOutDate: Long = 0L,
    @ColumnInfo(defaultValue = "1") val guests: Int = 1,
    @ColumnInfo(defaultValue = "1") val nights: Int = 1,
    val paymentMethod: String? = null,
    val transactionId: String? = null,
    val paidAt: Long? = null
)
