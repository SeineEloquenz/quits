package nz.eloque.quits.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The opaque per-record payload the relay stores. Group-id-free: which group a record belongs to is
 * contextual (the sync container), so the same payload works regardless of each device's local id.
 * The `type` discriminator routes a pulled record to the right table.
 */
@Serializable
sealed interface SyncPayload {
    @Serializable
    @SerialName("group")
    data class Group(
        val name: String,
        val baseCurrency: String,
    ) : SyncPayload

    @Serializable
    @SerialName("member")
    data class Member(
        val id: String,
        val name: String,
        val color: Long?,
    ) : SyncPayload

    @Serializable
    @SerialName("expense")
    data class Expense(
        val id: String,
        val title: String,
        val amountMinor: Long,
        val currency: String,
        val rateToBase: Double,
        val category: String?,
        val spentAt: Long,
        val note: String?,
        val splitType: String,
        val payers: List<Payer>,
        val splits: List<SplitLine>,
    ) : SyncPayload

    @Serializable
    @SerialName("settlement")
    data class Settlement(
        val id: String,
        val fromMember: String,
        val toMember: String,
        val amountMinor: Long,
        val currency: String,
        val rateToBase: Double,
        val paidAt: Long,
        val note: String?,
    ) : SyncPayload

    @Serializable
    data class Payer(
        val id: String,
        val memberId: String,
        val amountMinor: Long,
    )

    @Serializable
    data class SplitLine(
        val id: String,
        val memberId: String,
        val shareMinor: Long,
        val weight: Double?,
    )
}
