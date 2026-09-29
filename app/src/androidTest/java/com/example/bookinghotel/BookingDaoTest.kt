package com.example.bookinghotel

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bookinghotel.data.local.BookingDatabase
import com.example.bookinghotel.data.local.BookingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookingDaoTest {

    private lateinit var database: BookingDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            BookingDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertThenUpdateStatus_persistsExpectedBooking() = runBlocking {
        val dao = database.bookingDao()
        val localId = dao.insertBooking(
            BookingEntity(
                remoteBookingId = 10,
                roomId = 1,
                roomTypeKey = "standard",
                quantity = 2,
                pricePerNight = 50.0,
                totalPrice = 100.0,
                status = "PENDING_PAYMENT",
                createdAt = 1000L
            )
        )

        val inserted = dao.observeBookings().first().single()
        assertEquals(localId, inserted.localId)
        assertEquals("PENDING_PAYMENT", inserted.status)

        dao.updateBookingStatus(localId, "SUCCESS")

        val updated = dao.observeBookings().first().single()
        assertEquals("SUCCESS", updated.status)
        assertEquals(10, updated.remoteBookingId)
    }

    @Test
    fun observeBookings_ordersNewestBookingFirst() = runBlocking {
        val dao = database.bookingDao()

        dao.insertBooking(
            BookingEntity(
                remoteBookingId = 1,
                roomId = 1,
                roomTypeKey = "standard",
                quantity = 1,
                pricePerNight = 50.0,
                totalPrice = 50.0,
                status = "PENDING_PAYMENT",
                createdAt = 1000L
            )
        )
        dao.insertBooking(
            BookingEntity(
                remoteBookingId = 2,
                roomId = 2,
                roomTypeKey = "deluxe",
                quantity = 1,
                pricePerNight = 80.0,
                totalPrice = 80.0,
                status = "PENDING_PAYMENT",
                createdAt = 2000L
            )
        )

        val history = dao.observeBookings().first()
        assertEquals(listOf(2, 1), history.map { it.remoteBookingId })
    }

    @Test
    fun updatePaymentDetails_persistsReceiptFields() = runBlocking {
        val dao = database.bookingDao()
        val localId = dao.insertBooking(
            BookingEntity(
                remoteBookingId = 20,
                roomId = 1,
                roomTypeKey = "standard",
                quantity = 1,
                pricePerNight = 50.0,
                totalPrice = 100.0,
                status = "PENDING_PAYMENT",
                createdAt = 1000L,
                checkInDate = 2000L,
                checkOutDate = 3000L,
                guests = 2,
                nights = 2
            )
        )

        dao.updatePaymentDetails(
            localId = localId,
            status = "SUCCESS",
            paymentMethod = "QR",
            transactionId = "TXN-20",
            paidAt = 4000L
        )

        val updated = dao.observeBookings().first().single()
        assertEquals("SUCCESS", updated.status)
        assertEquals("QR", updated.paymentMethod)
        assertEquals("TXN-20", updated.transactionId)
        assertEquals(4000L, updated.paidAt)
    }
}
