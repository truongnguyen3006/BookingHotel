package com.example.bookinghotel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {

    @Query("SELECT * FROM bookings ORDER BY createdAt DESC, localId DESC")
    fun observeBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE ownerSessionId = :ownerSessionId")
    suspend fun getBookingsForSession(ownerSessionId: String): List<BookingEntity>

    /** Atomic replacement keeps local IDs and receipts stable across foreground/worker refresh. */
    @Transaction
    suspend fun replaceForSession(ownerSessionId: String, rows: List<BookingEntity>) {
        val previous = getBookingsForSession(ownerSessionId).associateBy { it.remoteBookingId }
        clearBookings()
        rows.forEach { row ->
            val old = previous[row.remoteBookingId]
            insertBooking(row.copy(
                ownerSessionId = ownerSessionId,
                localId = old?.localId ?: 0L,
                paymentMethod = old?.paymentMethod,
                transactionId = old?.transactionId,
                paidAt = old?.paidAt
            ))
        }
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBooking(booking: BookingEntity): Long

    @Query("DELETE FROM bookings")
    suspend fun clearBookings()

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
