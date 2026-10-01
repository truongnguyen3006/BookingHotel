package com.example.bookinghotel.data.remote.dto

data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String
)

data class LoginRequestDto(
    val email: String,
    val password: String
)

data class RefreshTokenRequestDto(val refreshToken: String)

data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: String
)

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: UserDto
)
