package com.example.bookinghotel.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.bookinghotel.BuildConfig
import com.example.bookinghotel.data.repository.RoomRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import retrofit2.HttpException

@HiltWorker
class RoomCatalogSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val roomRepository: RoomRepository
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val sync = roomRepository.syncRoomsFromNetwork()
        return sync.fold(
            onSuccess = {
                Result.success(
                    workDataOf(
                        KEY_ENVIRONMENT to BuildConfig.ENVIRONMENT,
                        KEY_SYNCED_AT to System.currentTimeMillis()
                    )
                )
            },
            onFailure = ::resultForFailure
        )
    }

    private fun resultForFailure(throwable: Throwable): Result {
        val retryable = throwable is IOException ||
            (throwable is HttpException && throwable.code() >= 500)
        return if (retryable && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure(workDataOf(KEY_ERROR to (throwable.message ?: "Room catalog sync failed")))
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "booking_hotel_room_catalog_periodic_sync"
        const val WORK_NAME_IMMEDIATE = "booking_hotel_room_catalog_immediate_sync"
        const val TAG = "booking_hotel_room_catalog_sync"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val KEY_ENVIRONMENT = "environment"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val KEY_ERROR = "error"
    }
}
