package nz.eloque.quits.sync

import android.content.Context
import nz.eloque.quits.data.sync.SyncScheduler

class WorkManagerSyncScheduler(
    private val context: Context,
) : SyncScheduler {
    override fun requestSync() = SyncWorker.requestNow(context)
}
