package nz.eloque.quits.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseCurrency: String,
    @Embedded val sync: SyncMeta,
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
    tableName = "expense",
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
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val rateToBase: Double,
    val category: String?,
    val spentAt: Long,
    val note: String?,
    val splitType: String,
    @Embedded val sync: SyncMeta,
)

@Entity(
    tableName = "expense_payer",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expenseId")],
)
data class ExpensePayerEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val memberId: String,
    val amountMinor: Long,
)

@Entity(
    tableName = "expense_split",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expenseId")],
)
data class ExpenseSplitEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val memberId: String,
    val shareMinor: Long,
    /** Split spec, read per the expense's splitType: share count for SHARES, percent for PERCENTAGE, null otherwise. */
    val weight: Double? = null,
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
    val note: String?,
    @Embedded val sync: SyncMeta,
)

@Entity(tableName = "fx_rate", primaryKeys = ["base", "quote"])
data class FxRateEntity(
    val base: String,
    val quote: String,
    val rate: Double,
    val asOf: Long,
)
