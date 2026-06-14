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
    fun group_sync_handle_round_trips() =
        runTest {
            val db = inMemoryDatabase()
            val dao = db.groupSyncDao()
            try {
                assertNull(dao.byGroup("g1"))
                dao.put(GroupSyncEntity("g1", remoteId = "r1", code = "ABC123", token = "tok", lastSeq = 0))
                assertEquals("r1", dao.byGroup("g1")?.remoteId)

                dao.setLastSeq("g1", 42)
                assertEquals(42, dao.byGroup("g1")?.lastSeq)
            } finally {
                db.close()
            }
        }
}
