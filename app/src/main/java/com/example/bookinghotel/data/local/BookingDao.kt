package com.example.bookinghotel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {

    @Query("SELECT * FROM bookings ORDER BY createdAt DESC, localId DESC")
    fun observeBookings(): Flow<List<BookingEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBooking(booking: BookingEntity): Long

    @Query("UPDATE bookings SET status = :status WHERE localId = :localId")
    suspend fun updateBookingStatus(localId: Long, status: String)

    @Query(
        """
        UPDATE bookings
        SET status = :status,
            paymentMethod = :paymentMethod,
            transactionId = :transactionId,
            paidAt = :paidAt
        WHERE localId = :localId
        """
    )
    suspend fun updatePaymentDetails(
        localId: Long,
        status: String,
        paymentMethod: String?,
        transactionId: String?,
        paidAt: Long?
    )
}
