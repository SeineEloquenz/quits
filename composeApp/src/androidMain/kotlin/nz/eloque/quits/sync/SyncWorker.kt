package nz.eloque.quits.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.data.sync.SyncRunResult
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result =
        try {
            when (engine.syncAll()) {
                SyncRunResult.Success -> Result.success()
                SyncRunResult.Retriable -> Result.retry()
                // Nothing transient failed — retrying the same work won't help, so don't.
                SyncRunResult.Permanent -> Result.failure()
            }
        } catch (e: Exception) {
            Result.retry()
        }

    companion object {
        private const val PERIODIC = "quits-sync-periodic"

        private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(networkConstraint)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
