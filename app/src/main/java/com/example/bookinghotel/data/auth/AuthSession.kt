package com.example.bookinghotel.data.auth

data class UserProfile(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: String
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long,
    val user: UserProfile,
    val sessionId: String = java.util.UUID.randomUUID().toString()
)
