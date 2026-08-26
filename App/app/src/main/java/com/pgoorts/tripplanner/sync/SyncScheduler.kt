package com.pgoorts.tripplanner.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PERIODIC_SYNC_WORK_NAME = "TripPlannerSync"
private const val MANUAL_SYNC_WORK_NAME = "TripPlannerManualSync"

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

    /** True if the device currently has a network connection capable of internet access. */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Enqueues a one-off sync run, replacing any manual sync already in flight, and returns the
     * new request's ID so the caller can observe *this* run specifically (see [observeSyncWorkInfo]).
     */
    fun triggerManualSync(): java.util.UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncWorker.KEY_IS_MANUAL to true))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        return request.id
    }

    /**
     * Emits [WorkInfo] updates for one specific sync run, identified by the ID returned from
     * [triggerManualSync]. Scoped to a single request (rather than the unique work name) so a
     * stale terminal state (FAILED/SUCCEEDED) from a *previous* manual sync never leaks into a
     * later screen/app open that hasn't triggered a sync of its own.
     */
    fun observeSyncWorkInfo(id: java.util.UUID): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id)
}
