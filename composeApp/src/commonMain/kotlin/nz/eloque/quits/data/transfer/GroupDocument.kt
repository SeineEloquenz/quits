package nz.eloque.quits.data.transfer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val GROUP_DOCUMENT_FORMAT = "quits-group"
internal const val GROUP_DOCUMENT_VERSION = 1

/** The exported file. Kept apart from the sync payload so the two formats can evolve independently. Ids are document-local references. */
@Serializable
internal data class GroupDocument(
    val format: String,
    val version: Int,
    val group: GroupDto,
    val members: List<MemberDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
    val settlements: List<SettlementDto> = emptyList(),
)

@Serializable
internal data class GroupDto(
    val name: String,
    val baseCurrency: String,
)

@Serializable
internal data class MemberDto(
    val id: String,
    val name: String,
)

@Serializable
internal data class CategoryDto(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
)

@Serializable
internal enum class EntryKindDto { EXPENSE, INCOME }

@Serializable
internal data class EntryDto(
    val id: String,
    val kind: EntryKindDto,
    val title: String,
    val currency: String,
    val rateToBase: Double,
    val spentAt: Long,
    val tzOffsetMinutes: Int,
    val categoryId: String?,
    val note: String?,
    val payments: List<PaymentDto>,
    val split: SplitDto,
)

@Serializable
internal data class PaymentDto(
    val member: String,
    val amountMinor: Long,
)

@Serializable
internal sealed interface SplitDto {
    @Serializable
    @SerialName("equal")
    data class Equal(
        val participants: List<String>,
    ) : SplitDto

    @Serializable
    @SerialName("shares")
    data class Shares(
        val shares: Map<String, Long>,
    ) : SplitDto

    @Serializable
    @SerialName("percentage")
    data class Percentage(
        val percent: Map<String, Int>,
    ) : SplitDto

    @Serializable
    @SerialName("exact")
    data class Exact(
        val amounts: Map<String, Long>,
    ) : SplitDto

    @Serializable
    @SerialName("itemized")
    data class Itemized(
        val items: List<ItemDto>,
    ) : SplitDto
}

@Serializable
internal data class ItemDto(
    val label: String,
    val amountMinor: Long,
    val participants: List<String>,
)

@Serializable
internal data class SettlementDto(
    val id: String,
    val from: String,
    val to: String,
    val amountMinor: Long,
    val currency: String,
    val rateToBase: Double,
    val paidAt: Long,
    val tzOffsetMinutes: Int,
    val note: String?,
)
