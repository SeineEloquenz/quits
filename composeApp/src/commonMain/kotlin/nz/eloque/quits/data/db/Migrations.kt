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
 * v5 -> v6: first-class categories. Adds a synced `category` table (custom, user-created categories)
 * and swaps `expense.category` (freeform string) for `expense.categoryId` (preset or custom id).
 * Freeform strings can't map to stable ids, so they're dropped (clean break, pre-launch). The expense
 * payload shape changed (`category` -> `categoryId`), a breaking sync change, so this is also a
 * flag day: clearing `group_sync` reverts groups to local-only and re-sharing mints fresh, versioned
 * records — no stale category-format record is ever pulled. Mirrors [MIGRATION_1_2]/[MIGRATION_3_4].
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `category` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, `deleted` INTEGER NOT NULL, " +
                    "`dirty` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_category_groupId` ON `category` (`groupId`)")
            connection.execSQL("ALTER TABLE expense DROP COLUMN category")
            connection.execSQL("ALTER TABLE expense ADD COLUMN categoryId TEXT")
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

/**
 * v6 -> v7: income events. Adds `expense.kind` ("EXPENSE"/"INCOME") so an entry can represent money
 * coming in. Additive — existing rows default to "EXPENSE"; income is the balance mirror of an
 * expense. No flag day: existing records stay valid.
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE expense ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'")
        }
    }

/**
 * v7 -> v8: rename the "expense" concept to "entry" now that it also models income. Pure rename of the
 * persisted names — table `expense` -> `entry`, its child tables `expense_*` -> `entry_*`, the
 * `expenseId` foreign-key columns -> `entryId`, and the indices — plus the sync payload type
 * (`@SerialName("expense")` -> `"entry"`), so it's also a flag day (clears `group_sync`). Renaming the
 * parent table auto-retargets child foreign keys; each renamed column's index is dropped and recreated
 * under Room's expected name.
 */
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE expense RENAME TO entry")
            connection.execSQL("DROP INDEX IF EXISTS index_expense_groupId")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_entry_groupId ON entry (groupId)")

            connection.execSQL("ALTER TABLE expense_payer RENAME TO entry_payer")
            connection.execSQL("ALTER TABLE entry_payer RENAME COLUMN expenseId TO entryId")
            connection.execSQL("DROP INDEX IF EXISTS index_expense_payer_expenseId")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_entry_payer_entryId ON entry_payer (entryId)")

            connection.execSQL("ALTER TABLE expense_split RENAME TO entry_split")
            connection.execSQL("ALTER TABLE entry_split RENAME COLUMN expenseId TO entryId")
            connection.execSQL("DROP INDEX IF EXISTS index_expense_split_expenseId")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_entry_split_entryId ON entry_split (entryId)")

            connection.execSQL("ALTER TABLE expense_item RENAME TO entry_item")
            connection.execSQL("ALTER TABLE entry_item RENAME COLUMN expenseId TO entryId")
            connection.execSQL("DROP INDEX IF EXISTS index_expense_item_expenseId")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_entry_item_entryId ON entry_item (entryId)")

            connection.execSQL("ALTER TABLE expense_item_participant RENAME TO entry_item_participant")
            connection.execSQL("DROP INDEX IF EXISTS index_expense_item_participant_itemId")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_entry_item_participant_itemId ON entry_item_participant (itemId)")

            connection.execSQL("DELETE FROM group_sync")
        }
    }

/**
 * v8 -> v9: archiving. Adds a local-only `group_prefs` table (currently just `archived`) that hides a
 * group into the drawer's "Archived" section. Kept out of the synced `groups` row so a sync update can
 * never clobber it. Additive, not synced; no flag day.
 */
val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `group_prefs` (`groupId` TEXT NOT NULL, `archived` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`groupId`), " +
                    "FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }
