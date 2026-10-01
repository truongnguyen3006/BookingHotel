package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.remote.dto.AuthResponseDto
import com.example.bookinghotel.data.remote.dto.LoginRequestDto
import com.example.bookinghotel.data.remote.dto.RefreshTokenRequestDto
import com.example.bookinghotel.data.remote.dto.RegisterRequestDto
import com.example.bookinghotel.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): AuthResponseDto

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequestDto): AuthResponseDto

    @POST("api/auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequestDto)

}
