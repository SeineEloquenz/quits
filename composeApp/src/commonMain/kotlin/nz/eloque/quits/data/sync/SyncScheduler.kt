package nz.eloque.quits.data.sync

interface SyncScheduler {
    fun requestSync()
}

object NoopSyncScheduler : SyncScheduler {
    override fun requestSync() = Unit
}
