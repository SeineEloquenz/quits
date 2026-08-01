package nz.eloque.quits.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseCurrency: String,
    @Embedded val sync: SyncMeta,
)

/**
 * Per-device, non-synced state for a group (currently just [archived]). Kept in its own table so the
 * sync-authoritative [GroupEntity] row can be rewritten wholesale without touching local preferences.
 */
@Entity(
    tableName = "group_prefs",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GroupPrefsEntity(
    @PrimaryKey val groupId: String,
    /** Hides the group into the drawer's "Archived" section. */
    val archived: Boolean = false,
)

@Entity(
    tableName = "member",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val color: Long? = null,
    @Embedded val sync: SyncMeta,
)

@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val rateToBase: Double,
    val categoryId: String?,
    val spentAt: Long,
    @ColumnInfo(defaultValue = "0") val tzOffsetMinutes: Int = 0,
    val note: String?,
    val splitType: String,
    @ColumnInfo(defaultValue = "EXPENSE") val kind: String = "EXPENSE",
    @Embedded val sync: SyncMeta,
)

@Entity(
    tableName = "entry_payer",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class EntryPayerEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val memberId: String,
    val amountMinor: Long,
)

@Entity(
    tableName = "entry_split",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class EntrySplitEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val memberId: String,
    val shareMinor: Long,
    /** Split spec, read per the entry's splitType: share count for SHARES, percent for PERCENTAGE, null otherwise. */
    val weight: Double? = null,
)

@Entity(
    tableName = "entry_item",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class EntryItemEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val label: String,
    val amountMinor: Long,
    /** Preserves the order items were entered in, so they reconstruct in the same sequence. */
    val position: Int,
)

@Entity(
    tableName = "entry_item_participant",
    primaryKeys = ["itemId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = EntryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class EntryItemParticipantEntity(
    val itemId: String,
    val memberId: String,
)

@Entity(
    tableName = "settlement",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class SettlementEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val fromMember: String,
    val toMember: String,
    val amountMinor: Long,
    val currency: String,
    val rateToBase: Double,
    val paidAt: Long,
    @ColumnInfo(defaultValue = "0") val tzOffsetMinutes: Int = 0,
    val note: String?,
    @Embedded val sync: SyncMeta,
)

@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val icon: String,
    val color: Long,
    @Embedded val sync: SyncMeta,
)

@Entity(tableName = "fx_rate", primaryKeys = ["base", "quote"])
data class FxRateEntity(
    val base: String,
    val quote: String,
    val rate: Double,
    val asOf: Long,
)
