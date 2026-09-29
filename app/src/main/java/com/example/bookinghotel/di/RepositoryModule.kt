package com.example.bookinghotel.di

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
    abstract fun bindRoomRepository(
        retrofitRoomRepository: RetrofitRoomRepository
    ): RoomRepository
}
