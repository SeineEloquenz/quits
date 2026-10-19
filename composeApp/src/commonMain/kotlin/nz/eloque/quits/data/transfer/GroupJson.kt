package nz.eloque.quits.data.transfer

import kotlinx.serialization.json.Json
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Entry
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Settlement
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.domain.Split
import nz.eloque.quits.util.newId

/** A file that cannot be turned into a valid group. */
class GroupImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Lossless JSON codec for a whole [Group]. */
object GroupJson {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    fun encode(group: Group): String = json.encodeToString(GroupDocument.serializer(), group.toDocument())

    /**
     * Rebuilds the group under fresh ids from [newId]. Members receive their fresh ids in the same
     * relative order as their original ids, because rounding ties in the split arithmetic break by member id.
     */
    fun decode(
        text: String,
        newId: () -> String = ::newId,
    ): Group {
        val document =
            try {
                json.decodeFromString(GroupDocument.serializer(), text)
            } catch (e: IllegalArgumentException) {
                throw GroupImportException("not a Quits group file", e)
            }
        if (document.format != GROUP_DOCUMENT_FORMAT) throw GroupImportException("not a Quits group file")
        if (document.version > GROUP_DOCUMENT_VERSION) throw GroupImportException("made by a newer version of Quits")
        return document.toGroup(newId)
    }
}

private fun Group.toDocument(): GroupDocument =
    GroupDocument(
        format = GROUP_DOCUMENT_FORMAT,
        version = GROUP_DOCUMENT_VERSION,
        group = GroupDto(name, baseCurrency.code),
        members = members.sortedBy { it.id.value }.map { MemberDto(it.id.value, it.name) },
        categories = categories.map { CategoryDto(it.id.value, it.name, it.icon, it.color) },
        entries = entries.sortedBy { it.spentAt }.map { it.toDto() },
        settlements = settlements.sortedBy { it.paidAt }.map { it.toDto() },
    )

private fun Entry.toDto(): EntryDto =
    EntryDto(
        id = id.value,
        kind = if (kind == EntryKind.INCOME) EntryKindDto.INCOME else EntryKindDto.EXPENSE,
        title = title,
        currency = currency.code,
        rateToBase = rateToBase,
        spentAt = spentAt,
        tzOffsetMinutes = tzOffsetMinutes,
        categoryId = categoryId?.value,
        note = note,
        payments = payments.map { PaymentDto(it.member.value, it.amount.minorUnits) },
        split = split.toDto(),
    )

private fun Split.toDto(): SplitDto =
    when (this) {
        is Split.Equal -> SplitDto.Equal(participants.map { it.value })
        is Split.Shares -> SplitDto.Shares(shares.mapKeys { it.key.value })
        is Split.Percentage -> SplitDto.Percentage(percent.mapKeys { it.key.value })
        is Split.Exact -> SplitDto.Exact(amounts.entries.associate { it.key.value to it.value.minorUnits })
        is Split.Itemized ->
            SplitDto.Itemized(items.map { item -> ItemDto(item.label, item.amount.minorUnits, item.participants.map { it.value }) })
    }

private fun Settlement.toDto(): SettlementDto =
    SettlementDto(
        id = id.value,
        from = from.value,
        to = to.value,
        amountMinor = amount.minorUnits,
        currency = amount.currency.code,
        rateToBase = rateToBase,
        paidAt = paidAt,
        tzOffsetMinutes = tzOffsetMinutes,
        note = note,
    )

private fun GroupDocument.toGroup(newId: () -> String): Group {
    val originalMemberIds = members.map { it.id }
    if (originalMemberIds.toSet().size != originalMemberIds.size) throw GroupImportException("duplicate member id")
    val memberIds =
        originalMemberIds
            .sorted()
            .zip(List(originalMemberIds.size) { newId() }.sorted())
            .associate { (original, fresh) -> original to MemberId(fresh) }
    val categoryIds = categories.associate { it.id to CategoryId(newId()) }

    fun member(ref: String): MemberId = requireNotNull(memberIds[ref]) { "unknown member $ref" }

    val baseCurrency = importing("group") { Currency.of(group.baseCurrency) }
    val entries =
        entries.mapIndexed { index, dto ->
            importing("entry ${index + 1} (${dto.title})") { dto.toEntry(EntryId(newId()), categoryIds, ::member) }
        }
    val settlements =
        settlements.mapIndexed { index, dto ->
            importing("settlement ${index + 1}") { dto.toSettlement(SettlementId(newId()), ::member) }
        }
    return importing("group") {
        Group(
            id = GroupId(newId()),
            name = group.name,
            baseCurrency = baseCurrency,
            members = members.map { Member(member(it.id), it.name) },
            entries = entries,
            settlements = settlements,
            categories = categories.map { Category(categoryIds.getValue(it.id), it.name, it.icon, it.color) },
        )
    }
}

private fun EntryDto.toEntry(
    id: EntryId,
    categoryIds: Map<String, CategoryId>,
    member: (String) -> MemberId,
): Entry {
    val currency = Currency.of(currency)
    return Entry(
        id = id,
        title = title,
        payments = payments.map { Payment(member(it.member), Money(it.amountMinor, currency)) },
        split = split.toSplit(currency, member),
        rateToBase = rateToBase,
        spentAt = spentAt,
        tzOffsetMinutes = tzOffsetMinutes,
        categoryId = categoryId?.let { categoryIds[it] ?: CategoryId(it) },
        note = note,
        kind = if (kind == EntryKindDto.INCOME) EntryKind.INCOME else EntryKind.EXPENSE,
    )
}

private fun SplitDto.toSplit(
    currency: Currency,
    member: (String) -> MemberId,
): Split =
    when (this) {
        is SplitDto.Equal -> Split.Equal(participants.map(member))
        is SplitDto.Shares -> Split.Shares(shares.mapKeys { member(it.key) })
        is SplitDto.Percentage -> Split.Percentage(percent.mapKeys { member(it.key) })
        is SplitDto.Exact -> Split.Exact(amounts.entries.associate { member(it.key) to Money(it.value, currency) })
        is SplitDto.Itemized ->
            Split.Itemized(
                items.map { item ->
                    Split.Itemized.Item(item.label, Money(item.amountMinor, currency), item.participants.map(member).toSet())
                },
            )
    }

private fun SettlementDto.toSettlement(
    id: SettlementId,
    member: (String) -> MemberId,
): Settlement =
    Settlement(
        id = id,
        from = member(from),
        to = member(to),
        amount = Money(amountMinor, Currency.of(currency)),
        rateToBase = rateToBase,
        paidAt = paidAt,
        tzOffsetMinutes = tzOffsetMinutes,
        note = note,
    )

private inline fun <T> importing(
    what: String,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: IllegalArgumentException) {
        throw GroupImportException("$what: ${e.message}", e)
    }

/** A filesystem-safe export file name derived from a group name, e.g. "Trip to Rome" -> "Trip to Rome.quits.json". */
fun exportFileName(groupName: String): String {
    val cleaned =
        groupName
            .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim()
            .ifEmpty { "group" }
    return "$cleaned.quits.json"
}
