package nz.eloque.quits.data.db

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
    @Test
    fun sync_state_round_trips() =
        runTest {
            val db = inMemoryDatabase()
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
