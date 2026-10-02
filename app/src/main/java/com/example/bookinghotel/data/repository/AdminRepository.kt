package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.AdminBooking
import com.example.bookinghotel.data.AdminDashboard
import com.example.bookinghotel.data.Room

interface AdminRepository {
    suspend fun getDashboard(): Result<AdminDashboard>
    suspend fun getRooms(): Result<List<Room>>
    suspend fun updateRoom(roomId: Int, pricePerNight: Long, availableRooms: Int): Result<Room>
    suspend fun getBookings(): Result<List<AdminBooking>>
}
