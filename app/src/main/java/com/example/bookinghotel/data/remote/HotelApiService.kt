package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.remote.dto.BookingRequestDto
import com.example.bookinghotel.data.remote.dto.BookingResponseDto
import com.example.bookinghotel.data.remote.dto.PaymentRequestDto
import com.example.bookinghotel.data.remote.dto.PaymentResponseDto
import com.example.bookinghotel.data.remote.dto.RoomDto
import com.example.bookinghotel.data.remote.dto.VnPayCreateRequestDto
import com.example.bookinghotel.data.remote.dto.VnPayCreateResponseDto
import com.example.bookinghotel.data.remote.dto.VnPayStatusResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface HotelApiService {

    @GET("api/rooms")
    suspend fun getRooms(): List<RoomDto>

    @GET("api/bookings")
    suspend fun getBookings(): List<BookingResponseDto>

    @POST("api/bookings")
    suspend fun createBooking(
        @Body request: BookingRequestDto
    ): BookingResponseDto

    @POST("api/bookings/{bookingId}/payment")
    suspend fun payBooking(
        @Path("bookingId") bookingId: Int,
        @Body request: PaymentRequestDto
    ): PaymentResponseDto

    @POST("api/bookings/{bookingId}/payment/vnpay")
    suspend fun createVnPayPayment(
        @Path("bookingId") bookingId: Int,
        @Body request: VnPayCreateRequestDto
    ): VnPayCreateResponseDto

    @GET("api/bookings/{bookingId}/payment/vnpay/status")
    suspend fun getVnPayPaymentStatus(
        @Path("bookingId") bookingId: Int
    ): VnPayStatusResponseDto
}
