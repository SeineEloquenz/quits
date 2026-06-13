package nz.eloque.quits.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuitsDatabaseTest {
    private fun database(): QuitsDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Production uses BundledSQLiteDriver; on the host we use the framework driver that
        // Robolectric backs with a real SQLite, avoiding the bundled native-lib (jniLibs) loader.
        return Room
            .inMemoryDatabaseBuilder<QuitsDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @Test
    fun sync_state_round_trips() =
        runTest {
            val db = database()
            val dao = db.syncStateDao()
            try {
                assertNull(dao.lastSeq("g1"))
                dao.put(SyncStateEntity("g1", 42))
                assertEquals(42, dao.lastSeq("g1"))
                dao.put(SyncStateEntity("g1", 99))
                assertEquals(99, dao.lastSeq("g1"))
            } finally {
                db.close()
            }
        }
}
