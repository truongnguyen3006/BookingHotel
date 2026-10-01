package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.remote.dto.AdminBookingDto
import com.example.bookinghotel.data.remote.dto.AdminDashboardDto
import com.example.bookinghotel.data.remote.dto.AdminRoomUpdateRequestDto
import com.example.bookinghotel.data.remote.dto.RoomDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface AdminApiService {
    @GET("api/admin/dashboard")
    suspend fun getDashboard(): AdminDashboardDto

    @GET("api/admin/rooms")
    suspend fun getRooms(): List<RoomDto>

    @PATCH("api/admin/rooms/{roomId}")
    suspend fun updateRoom(
        @Path("roomId") roomId: Int,
        @Body request: AdminRoomUpdateRequestDto
    ): RoomDto

    @GET("api/admin/bookings")
    suspend fun getBookings(): List<AdminBookingDto>
}
