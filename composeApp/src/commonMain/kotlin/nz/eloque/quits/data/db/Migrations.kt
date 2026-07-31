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

/**
 * v3 -> v4 (payload-versioning flag day): sync payloads are now wrapped in a versioned envelope
 * (see [nz.eloque.quits.data.sync.CURRENT_PAYLOAD_VERSION]). Relay records written by earlier
 * versions are bare, unversioned payloads that a new client can't decode. Clearing the sync handles
 * reverts shared groups to local-only so no such record is ever pulled; expense data is untouched
 * and the user re-shares to mint a fresh, versioned encrypted group. Mirrors [MIGRATION_1_2].
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DELETE FROM group_sync")
        }
    }

/**
 * v4 -> v5: timezone-aware dates. Adds `tzOffsetMinutes` to `expense` and `settlement` — the UTC
 * offset captured when the date was entered, so the day/time renders as the enterer meant it
 * regardless of the viewer's timezone. Additive; existing rows default to 0 (UTC).
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE expense ADD COLUMN tzOffsetMinutes INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE settlement ADD COLUMN tzOffsetMinutes INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * v2 -> v3: itemized splits. Adds the `expense_item` line-item table and its `expense_item_participant`
 * join table. Purely additive — existing expenses (equal/shares/percentage/exact) are untouched.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `expense_item` (" +
                    "`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                    "`amountMinor` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`expenseId`) REFERENCES `expense`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_expense_item_expenseId` ON `expense_item` (`expenseId`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `expense_item_participant` (" +
                    "`itemId` TEXT NOT NULL, `memberId` TEXT NOT NULL, PRIMARY KEY(`itemId`, `memberId`), " +
                    "FOREIGN KEY(`itemId`) REFERENCES `expense_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_expense_item_participant_itemId` " +
                    "ON `expense_item_participant` (`itemId`)",
            )
        }
    }
