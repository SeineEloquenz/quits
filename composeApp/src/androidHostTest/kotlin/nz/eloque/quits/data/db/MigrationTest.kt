package nz.eloque.quits.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun migrating_9_to_10_adds_the_default_split_and_resets_sync_positions() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val path = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs() }.path
            AndroidSQLiteDriver().open(path).use { connection ->
                createSchema(connection::execSQL, 9)
                connection.execSQL("INSERT INTO group_sync VALUES ('g1', 'r1', 'code', 'tok', 7, 0)")
                connection.execSQL("PRAGMA user_version = 9")
            }

            val db =
                Room
                    .databaseBuilder<QuitsDatabase>(context, path)
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .addMigrations(MIGRATION_9_10)
                    .build()
            try {
                assertEquals(0, db.groupSyncDao().byGroup("g1")?.lastSeq)
                assertEquals(null, db.defaultSplitDao().byGroup("g1"))
            } finally {
                db.close()
                context.deleteDatabase("migration-test.db")
            }
        }

    private fun createSchema(
        exec: (String) -> Unit,
        version: Int,
    ) {
        val schema =
            Json.parseToJsonElement(File("schemas/nz.eloque.quits.data.db.QuitsDatabase/$version.json").readText())
                .jsonObject["database"]!!
                .jsonObject
        schema["entities"]!!.jsonArray.forEach { entity ->
            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
            exec(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            (entity.jsonObject["indices"]?.jsonArray ?: emptyList()).forEach {
                exec((it as JsonObject)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { exec(it.jsonPrimitive.content) }
    }
}
