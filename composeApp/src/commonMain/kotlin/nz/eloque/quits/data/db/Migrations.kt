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
