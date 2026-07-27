package nz.eloque.quits.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 -> v2 (E2EE flag day): pre-E2EE sync handles reference server-issued share codes that no longer
 * resolve. Clearing them reverts shared groups to local-only; their expense data is untouched and
 * the user re-shares to mint an encrypted group.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DELETE FROM group_sync")
        }
    }
