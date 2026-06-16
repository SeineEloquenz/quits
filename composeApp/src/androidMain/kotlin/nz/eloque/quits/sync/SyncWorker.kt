package nz.eloque.quits.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nz.eloque.quits.data.sync.SyncEngine
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
            if (engine.syncAll()) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }

    companion object {
        private const val PERIODIC = "quits-sync-periodic"
        private const val ONE_TIME = "quits-sync-now"

        private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(networkConstraint)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun requestNow(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(networkConstraint)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
