package nz.eloque.quits.data.repository

import kotlinx.serialization.json.Json
import nz.eloque.quits.data.db.CategoryEntity
import nz.eloque.quits.data.db.DefaultSplitEntity
import nz.eloque.quits.data.db.EntryEntity
import nz.eloque.quits.data.db.EntryItemEntity
import nz.eloque.quits.data.db.EntryItemParticipantEntity
import nz.eloque.quits.data.db.EntryPayerEntity
import nz.eloque.quits.data.db.EntrySplitEntity
import nz.eloque.quits.data.db.EntryWithLines
import nz.eloque.quits.data.db.ItemWithParticipants
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.SettlementEntity
import nz.eloque.quits.data.db.SyncMeta
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.DefaultSplit
import nz.eloque.quits.domain.Entry
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Settlement
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.domain.Split

internal const val SPLIT_EQUAL = "EQUAL"
internal const val SPLIT_SHARES = "SHARES"
internal const val SPLIT_PERCENTAGE = "PERCENTAGE"
internal const val SPLIT_EXACT = "EXACT"
internal const val SPLIT_ITEMIZED = "ITEMIZED"

private val KNOWN_SPLIT_TYPES = setOf(SPLIT_EQUAL, SPLIT_SHARES, SPLIT_PERCENTAGE, SPLIT_EXACT, SPLIT_ITEMIZED)

internal fun splitTypeName(split: Split): String =
    when (split) {
        is Split.Equal -> SPLIT_EQUAL
        is Split.Shares -> SPLIT_SHARES
        is Split.Percentage -> SPLIT_PERCENTAGE
        is Split.Exact -> SPLIT_EXACT
        is Split.Itemized -> SPLIT_ITEMIZED
    }

internal fun MemberEntity.toDomain(): Member = Member(MemberId(id), name)

/** Parses a stored entry-kind string; an unrecognized value (e.g. a newer kind) reads as EXPENSE. */
private fun entryKindOf(value: String): EntryKind = if (value == "INCOME") EntryKind.INCOME else EntryKind.EXPENSE

internal fun CategoryEntity.toDomain(): Category = Category(CategoryId(id), name, icon, color)

internal fun SettlementEntity.toDomain(): Settlement =
    Settlement(
        SettlementId(id),
        MemberId(fromMember),
        MemberId(toMember),
        Money(amountMinor, Currency.of(currency)),
        rateToBase,
        paidAt,
        tzOffsetMinutes,
        note,
    )

internal fun EntryWithLines.toDomain(): Entry {
    val currency = Currency.of(entry.currency)
    val payments = payers.map { Payment(MemberId(it.memberId), Money(it.amountMinor, currency)) }
    return Entry(
        EntryId(entry.id),
        entry.title,
        payments,
        toSplit(entry.splitType, splits, items, currency),
        entry.rateToBase,
        entry.spentAt,
        entry.tzOffsetMinutes,
        entry.categoryId?.let { CategoryId(it) },
        entry.note,
        entryKindOf(entry.kind),
        splitSupported = entry.splitType in KNOWN_SPLIT_TYPES,
    )
}

private fun toSplit(
    type: String,
    rows: List<EntrySplitEntity>,
    items: List<ItemWithParticipants>,
    currency: Currency,
): Split =
    when (type) {
        SPLIT_EQUAL -> Split.Equal(rows.map { MemberId(it.memberId) })
        SPLIT_SHARES -> Split.Shares(rows.associate { MemberId(it.memberId) to (it.weight ?: 0.0).toLong() })
        SPLIT_PERCENTAGE -> Split.Percentage(rows.associate { MemberId(it.memberId) to (it.weight ?: 0.0).toInt() })
        SPLIT_EXACT -> Split.Exact(rows.associate { MemberId(it.memberId) to Money(it.shareMinor, currency) })
        SPLIT_ITEMIZED ->
            Split.Itemized(
                items
                    .sortedBy { it.item.position }
                    .map { iwp ->
                        Split.Itemized.Item(
                            iwp.item.label,
                            Money(iwp.item.amountMinor, currency),
                            iwp.participants.map { MemberId(it.memberId) }.toSet(),
                        )
                    },
            )
        // Forward-compat: an unrecognized (newer) split type. Never throw on well-formed synced data —
        // rebuild from the stored per-member shares (the caller flags it read-only).
        else -> Split.Exact(rows.associate { MemberId(it.memberId) to Money(it.shareMinor, currency) })
    }

internal fun Entry.toEntity(
    groupId: GroupId,
    spentAt: Long,
    sync: SyncMeta,
): EntryEntity =
    EntryEntity(
        id = id.value,
        groupId = groupId.value,
        title = title,
        amountMinor = total.minorUnits,
        currency = currency.code,
        rateToBase = rateToBase,
        categoryId = categoryId?.value,
        spentAt = spentAt,
        tzOffsetMinutes = tzOffsetMinutes,
        note = note,
        splitType = splitTypeName(split),
        kind = kind.name,
        sync = sync,
    )

internal fun Settlement.toEntity(
    groupId: GroupId,
    paidAt: Long,
    note: String?,
    sync: SyncMeta,
): SettlementEntity =
    SettlementEntity(
        id = id.value,
        groupId = groupId.value,
        fromMember = from.value,
        toMember = to.value,
        amountMinor = amount.minorUnits,
        currency = amount.currency.code,
        rateToBase = rateToBase,
        paidAt = paidAt,
        tzOffsetMinutes = tzOffsetMinutes,
        note = note,
        sync = sync,
    )

/** Payer lines for an entry; synthetic ids since multiple payments may share a member. */
internal fun payerRows(entry: Entry): List<EntryPayerEntity> =
    entry.payments.mapIndexed { i, payment ->
        EntryPayerEntity("${entry.id.value}:payer:$i", entry.id.value, payment.member.value, payment.amount.minorUnits)
    }

/** Split lines for an entry: the materialized share per member plus the spec weight (if any). */
internal fun splitRows(entry: Entry): List<EntrySplitEntity> {
    val eid = entry.id.value
    val split = entry.split
    val members: Collection<MemberId> =
        when (split) {
            is Split.Equal -> split.participants
            is Split.Shares -> split.shares.keys
            is Split.Percentage -> split.percent.keys
            is Split.Exact -> split.amounts.keys
            is Split.Itemized -> split.items.flatMap { it.participants }.toSet()
        }
    return members.map { member ->
        val weight: Double? =
            when (split) {
                is Split.Shares -> split.shares.getValue(member).toDouble()
                is Split.Percentage -> split.percent.getValue(member).toDouble()
                else -> null
            }
        EntrySplitEntity("$eid:${member.value}", eid, member.value, entry.shareOf(member).minorUnits, weight)
    }
}

/** Item id shared by an item row and its participant rows; position-based so it round-trips stably. */
private fun itemId(
    entryId: String,
    position: Int,
): String = "$entryId:item:$position"

/** Line-item rows for an itemized entry; empty for every other split kind. */
internal fun itemRows(entry: Entry): List<EntryItemEntity> {
    val split = entry.split
    if (split !is Split.Itemized) return emptyList()
    return split.items.mapIndexed { i, item ->
        EntryItemEntity(itemId(entry.id.value, i), entry.id.value, item.label, item.amount.minorUnits, i)
    }
}

/** Item-to-member assignment rows for an itemized entry; empty for every other split kind. */
internal fun itemParticipantRows(entry: Entry): List<EntryItemParticipantEntity> {
    val split = entry.split
    if (split !is Split.Itemized) return emptyList()
    return split.items.flatMapIndexed { i, item ->
        item.participants.map { EntryItemParticipantEntity(itemId(entry.id.value, i), it.value) }
    }
}

/** Null for a kind this version doesn't know, so a newer client's default reads as no default. */
internal fun DefaultSplitEntity.toDomain(): DefaultSplit? {
    val kind = DefaultSplit.Kind.entries.firstOrNull { it.name == kind } ?: return null
    return DefaultSplit(kind, Json.decodeFromString<Map<String, Long>>(weights).mapKeys { MemberId(it.key) })
}

internal fun DefaultSplit.toEntity(
    groupId: GroupId,
    sync: SyncMeta,
): DefaultSplitEntity = DefaultSplitEntity(groupId.value, kind.name, Json.encodeToString(weights.mapKeys { it.key.value }), sync)
