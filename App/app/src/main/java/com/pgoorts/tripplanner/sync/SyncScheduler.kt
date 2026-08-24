package com.pgoorts.tripplanner.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PERIODIC_SYNC_WORK_NAME = "TripPlannerSync"

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * (Re-)enqueues the periodic background sync job at [intervalMinutes].
     * [policy] defaults to KEEP (app-startup path: don't reset an already-running schedule);
     * pass REPLACE when the user has just changed the interval in Settings.
     */
    fun schedulePeriodic(
        intervalMinutes: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            policy,
            syncWorkRequest
        )
    }
}
