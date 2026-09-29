package com.example.bookinghotel.di

import android.content.Context
import androidx.room.Room
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.local.BookingDatabase
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
            .addMigrations(BookingDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideBookingDao(database: BookingDatabase): BookingDao {
        return database.bookingDao()
    }
}
