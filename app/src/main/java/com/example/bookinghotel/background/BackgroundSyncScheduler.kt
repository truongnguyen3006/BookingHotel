package com.example.bookinghotel.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundSyncScheduler {

    fun schedulePeriodicSync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        // Clean up names from the pre-Phase-3 combined worker after app upgrade.
        workManager.cancelUniqueWork(BackgroundSyncWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(BackgroundSyncWorker.WORK_NAME_IMMEDIATE)

        workManager.enqueueUniquePeriodicWork(
            RoomCatalogSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RoomCatalogSyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(RoomCatalogSyncWorker.TAG)
                .build()
        )

        workManager.enqueueUniquePeriodicWork(
            BookingHistorySyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BookingHistorySyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(BookingHistorySyncWorker.TAG)
                .build()
        )
    }

    /** Schedules room and user-history sync independently so one failure never blocks the other. */
    fun enqueueImmediateSync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            RoomCatalogSyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RoomCatalogSyncWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(RoomCatalogSyncWorker.TAG)
                .build()
        )
        workManager.enqueueUniqueWork(
            BookingHistorySyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BookingHistorySyncWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(BookingHistorySyncWorker.TAG)
                .build()
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private const val PERIODIC_INTERVAL_HOURS = 6L
    private const val BACKOFF_SECONDS = 30L
}
