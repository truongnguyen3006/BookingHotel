package com.example.bookinghotel.di

import android.content.Context
import androidx.room.Room
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.local.BookingDatabase
import com.example.bookinghotel.data.local.RoomCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBookingDatabase(
        @ApplicationContext context: Context
    ): BookingDatabase {
        return Room.databaseBuilder(
            context,
            BookingDatabase::class.java,
            "booking_hotel.db"
        )
            .addMigrations(
                BookingDatabase.MIGRATION_1_2,
                BookingDatabase.MIGRATION_2_3
            )
            .build()
    }

    @Provides
    fun provideBookingDao(database: BookingDatabase): BookingDao {
        return database.bookingDao()
    }

    @Provides
    fun provideRoomCacheDao(database: BookingDatabase): RoomCacheDao {
        return database.roomCacheDao()
    }
}
