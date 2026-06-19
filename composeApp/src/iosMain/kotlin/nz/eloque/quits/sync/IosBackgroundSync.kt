package nz.eloque.quits.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nz.eloque.quits.data.sync.SyncEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

@OptIn(ExperimentalForeignApi::class)
object IosBackgroundSync : KoinComponent {
    const val TASK_ID = "nz.eloque.quits.sync"

    private val engine: SyncEngine by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Registers the launch handler. Must be called before the app finishes launching. */
    fun register() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(TASK_ID, null) { task: BGTask? ->
            (task as? BGAppRefreshTask)?.let { handle(it) }
        }
    }

    /** Asks the system to run a refresh no sooner than ~15 min from now. Best-effort. */
    fun schedule() {
        val request = BGAppRefreshTaskRequest(TASK_ID)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(15.0 * 60)
        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        } catch (e: Exception) {
            // Submission can fail (simulator, too many pending) — background sync is best-effort.
        }
    }

    private fun handle(task: BGAppRefreshTask) {
        schedule() // chain the next refresh
        val job =
            scope.launch {
                val ok = runCatching { engine.syncAll() }.getOrDefault(false)
                task.setTaskCompletedWithSuccess(ok)
            }
        task.expirationHandler = { job.cancel() }
    }
}
