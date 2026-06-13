package nz.eloque.quits.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers

/**
 * In-memory [QuitsDatabase] for host tests. Production uses BundledSQLiteDriver; on the host we use
 * the framework driver that Robolectric backs with a real SQLite (the bundled native lib isn't on
 * the host's java.library.path). Must be called from a Robolectric-run test.
 */
internal fun inMemoryDatabase(): QuitsDatabase =
    Room
        .inMemoryDatabaseBuilder<QuitsDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

internal fun meta(updatedAt: Long = 1L) = SyncMeta(updatedAt = updatedAt, deviceId = "test-device")
