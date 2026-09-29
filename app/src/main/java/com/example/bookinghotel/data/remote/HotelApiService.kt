package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.remote.dto.BookingRequestDto
import com.example.bookinghotel.data.remote.dto.BookingResponseDto
import com.example.bookinghotel.data.remote.dto.RoomDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface HotelApiService {

    @GET("api/rooms")
    suspend fun getRooms(): List<RoomDto>

    @POST("api/bookings")
    suspend fun createBooking(
        @Body request: BookingRequestDto
    ): BookingResponseDto
}
