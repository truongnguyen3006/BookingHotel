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
        val constraints = networkConstraints()
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            PERIODIC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(BackgroundSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BackgroundSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Useful after login or when a user explicitly asks for a refresh.
     * Booking/payment mutation is intentionally never queued here; those operations
     * must be confirmed by the server while the user is online.
     */
    fun enqueueImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(BackgroundSyncWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            BackgroundSyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private const val PERIODIC_INTERVAL_HOURS = 6L
    private const val BACKOFF_SECONDS = 30L
}
