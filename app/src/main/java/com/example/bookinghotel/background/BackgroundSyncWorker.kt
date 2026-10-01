package com.example.bookinghotel.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.bookinghotel.BuildConfig
import com.example.bookinghotel.data.repository.AuthRepository
import com.example.bookinghotel.data.repository.RoomRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

@HiltWorker
class BackgroundSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val roomRepository: RoomRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val roomSync = roomRepository.syncRoomsFromNetwork()
        if (roomSync.isFailure) {
            return resultForFailure(roomSync.exceptionOrNull())
        }

        // Booking history is user-specific. Public room inventory can still sync
        // when no user is signed in.
        val signedIn = authRepository.session.first() != null
        if (signedIn) {
            val historySync = roomRepository.refreshBookingHistory()
            if (historySync.isFailure) {
                return resultForFailure(historySync.exceptionOrNull())
            }
        }

        return Result.success(
            workDataOf(
                KEY_ENVIRONMENT to BuildConfig.ENVIRONMENT,
                KEY_SYNCED_AT to System.currentTimeMillis(),
                KEY_HISTORY_SYNCED to signedIn
            )
        )
    }

    private fun resultForFailure(throwable: Throwable?): Result {
        val retryable = throwable is IOException ||
            (throwable is HttpException && throwable.code() >= 500)

        return if (retryable && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure(
                workDataOf(KEY_ERROR to (throwable?.message ?: "Background sync failed"))
            )
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "booking_hotel_periodic_sync"
        const val WORK_NAME_IMMEDIATE = "booking_hotel_immediate_sync"
        const val TAG = "booking_hotel_sync"

        private const val MAX_RETRY_ATTEMPTS = 3
        private const val KEY_ENVIRONMENT = "environment"
        private const val KEY_SYNCED_AT = "synced_at"
        private const val KEY_HISTORY_SYNCED = "history_synced"
        private const val KEY_ERROR = "error"
    }
}
