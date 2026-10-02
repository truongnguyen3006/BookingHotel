package com.example.bookinghotel.di

import com.example.bookinghotel.data.repository.AdminRepository
import com.example.bookinghotel.data.repository.AuthRepository
import com.example.bookinghotel.data.repository.RetrofitAdminRepository
import com.example.bookinghotel.data.repository.RetrofitAuthRepository
import com.example.bookinghotel.data.repository.RetrofitRoomRepository
import com.example.bookinghotel.data.repository.RoomRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAppStrings(strings: com.example.bookinghotel.ui.AndroidAppStrings): com.example.bookinghotel.ui.AppStrings

    @Binds
    @Singleton
    abstract fun bindSessionStore(tokenStore: com.example.bookinghotel.data.auth.TokenStore): com.example.bookinghotel.data.auth.SessionStore


    @Binds
    @Singleton
    abstract fun bindRoomRepository(
        retrofitRoomRepository: RetrofitRoomRepository
    ): RoomRepository

    @Binds
    @Singleton
    abstract fun bindAdminRepository(
        retrofitAdminRepository: RetrofitAdminRepository
    ): AdminRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        retrofitAuthRepository: RetrofitAuthRepository
    ): AuthRepository
}
