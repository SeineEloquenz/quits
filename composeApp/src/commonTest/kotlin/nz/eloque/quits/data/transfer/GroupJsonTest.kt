package nz.eloque.quits.data.transfer

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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GroupJsonTest {
    private val eur = Currency.of("EUR")
    private val usd = Currency.of("USD")
    private val jpy = Currency.of("JPY")
    private val bhd = Currency.of("BHD")

    private val m1 = MemberId("m1")
    private val m2 = MemberId("m2")
    private val m3 = MemberId("m3")
    private val m4 = MemberId("m4")

    private fun richGroup(): Group {
        val equal =
            Entry(
                EntryId("e-equal"),
                "Dinner",
                listOf(Payment(m3, Money(1000, eur))),
                Split.Equal(listOf(m1, m2, m3)),
                spentAt = 1_760_837_400_125L,
                tzOffsetMinutes = 780,
                categoryId = CategoryId("dining"),
            )
        val shares =
            Entry(
                EntryId("e-shares"),
                "Ramen",
                listOf(Payment(m1, Money(600, jpy)), Payment(m1, Money(401, jpy))),
                Split.Shares(mapOf(m1 to 2L, m2 to 1L)),
                rateToBase = 0.0061234567891,
                spentAt = 1_700_000_000_001L,
                tzOffsetMinutes = 540,
                categoryId = CategoryId("cat-1"),
                note = "Said \"thanks\",\nthen left; = @ + \\ ok",
            )
        val percentage =
            Entry(
                EntryId("e-pct"),
                "Refund",
                listOf(Payment(m2, Money(12345, bhd))),
                Split.Percentage(mapOf(m2 to 33, m3 to 67)),
                rateToBase = 2.43,
                spentAt = 0L,
                tzOffsetMinutes = 345,
                kind = EntryKind.INCOME,
            )
        val exact =
            Entry(
                EntryId("e-exact"),
                "Hotel",
                listOf(Payment(m1, Money(1000, eur))),
                Split.Exact(mapOf(m1 to Money(700, eur), m3 to Money(300, eur))),
                spentAt = 1_600_000_000_000L,
                categoryId = CategoryId("gone-category"),
            )
        val itemized =
            Entry(
                EntryId("e-items"),
                "Groceries",
                listOf(Payment(m3, Money(2301, usd))),
                Split.Itemized(
                    listOf(
                        Split.Itemized.Item("Pizza", Money(2000, usd), setOf(m1, m2)),
                        Split.Itemized.Item("Discount", Money(-500, usd), setOf(m1)),
                        Split.Itemized.Item("Beer", Money(801, usd), setOf(m2, m3)),
                    ),
                ),
                rateToBase = 0.92,
                spentAt = 1_650_000_000_000L,
            )
        return Group(
            GroupId("g"),
            "Trip to Rome",
            eur,
            listOf(Member(m3, "Alice"), Member(m1, "Bob"), Member(m2, "Bob"), Member(m4, "Dora")),
            listOf(equal, shares, percentage, exact, itemized),
            listOf(
                Settlement(
                    SettlementId("s"),
                    from = m2,
                    to = m3,
                    amount = Money(1234, eur),
                    rateToBase = 1.1,
                    paidAt = 1_700_000_000_000L,
                    tzOffsetMinutes = -300,
                    note = "cash",
                ),
            ),
            listOf(Category(CategoryId("cat-1"), "Snacks", "fastfood", 0xFF4CAF50)),
        )
    }

    /** Ids that sort in the reverse order of their creation, so a naive remap would reorder members. */
    private fun descendingIds(): () -> String {
        var next = 999
        return { "new-${next--}" }
    }

    /** Everything a group carries, with member and category ids replaced by stable positions. */
    private fun canonical(group: Group): String {
        val rank = group.members.sortedBy { it.id.value }.withIndex().associate { (i, m) -> m.id to "#$i" }
        val categoryRank = group.categories.withIndex().associate { (i, c) -> c.id to "cat#$i" }

        fun m(id: MemberId) = rank.getValue(id)

        fun split(split: Split): String =
            when (split) {
                is Split.Equal -> "equal ${split.participants.map(::m)}"
                is Split.Shares -> "shares ${split.shares.mapKeys { m(it.key) }}"
                is Split.Percentage -> "percent ${split.percent.mapKeys { m(it.key) }}"
                is Split.Exact -> "exact ${split.amounts.mapKeys { m(it.key) }}"
                is Split.Itemized -> "items ${split.items.map { "${it.label} ${it.amount} ${it.participants.map(::m).sorted()}" }}"
            }
        return buildString {
            appendLine("${group.name} ${group.baseCurrency}")
            group.members.sortedBy { it.id.value }.forEach { appendLine("member ${m(it.id)} ${it.name}") }
            group.categories.forEach { appendLine("category ${categoryRank.getValue(it.id)} ${it.name} ${it.icon} ${it.color}") }
            group.entries.sortedBy { it.title }.forEach { e ->
                appendLine(
                    listOf(
                        e.kind,
                        e.title,
                        e.payments.map { "${m(it.member)}=${it.amount}" },
                        split(e.split),
                        e.shares.mapKeys { m(it.key) },
                        e.rateToBase,
                        e.spentAt,
                        e.tzOffsetMinutes,
                        e.categoryId?.let { categoryRank[it] ?: it.value },
                        e.note,
                    ).joinToString(" | "),
                )
            }
            group.settlements.forEach { s ->
                appendLine("settle ${m(s.from)}->${m(s.to)} ${s.amount} ${s.rateToBase} ${s.paidAt} ${s.tzOffsetMinutes} ${s.note}")
            }
            group.balances().net.entries.sortedBy { m(it.key) }.forEach { (id, net) -> appendLine("balance ${m(id)} $net") }
        }
    }

    @Test
    fun round_trip_preserves_everything() {
        val original = richGroup()

        val restored = GroupJson.decode(GroupJson.encode(original), descendingIds())

        assertEquals(canonical(original), canonical(restored))
    }

    @Test
    fun remainder_cents_land_on_the_same_member() {
        val original = richGroup()

        val restored = GroupJson.decode(GroupJson.encode(original), descendingIds())

        val position = { g: Group, id: MemberId -> g.members.sortedBy { it.id.value }.indexOfFirst { it.id == id } }
        val dinnerShares = { g: Group -> g.entries.first { it.title == "Dinner" }.shares.mapKeys { position(g, it.key) } }
        assertEquals(dinnerShares(original), dinnerShares(restored))
        assertEquals(setOf(333L, 334L), dinnerShares(restored).values.map { it.minorUnits }.toSet())
    }

    @Test
    fun import_assigns_fresh_ids() {
        val original = richGroup()

        val restored = GroupJson.decode(GroupJson.encode(original), descendingIds())

        val originalIds =
            buildSet {
                add(original.id.value)
                addAll(original.members.map { it.id.value })
                addAll(original.entries.map { it.id.value })
                addAll(original.settlements.map { it.id.value })
                addAll(original.categories.map { it.id.value })
            }
        val restoredIds =
            listOf(restored.id.value) +
                restored.members.map { it.id.value } +
                restored.entries.map { it.id.value } +
                restored.settlements.map { it.id.value } +
                restored.categories.map { it.id.value }
        assertTrue(restoredIds.none { it in originalIds })
        assertEquals(restoredIds.size, restoredIds.toSet().size)
    }

    @Test
    fun custom_category_reference_follows_the_new_id() {
        val restored = GroupJson.decode(GroupJson.encode(richGroup()), descendingIds())

        val ramen = restored.entries.first { it.title == "Ramen" }
        assertEquals(restored.categories.single().id, ramen.categoryId)
        assertEquals(CategoryId("dining"), restored.entries.first { it.title == "Dinner" }.categoryId)
    }

    @Test
    fun empty_group_round_trips() {
        val original = Group(GroupId("g"), "Empty", usd, emptyList())

        val restored = GroupJson.decode(GroupJson.encode(original))

        assertEquals(canonical(original), canonical(restored))
    }

    private fun document(
        format: String = GROUP_DOCUMENT_FORMAT,
        version: Int = GROUP_DOCUMENT_VERSION,
        baseCurrency: String = "EUR",
        payer: String = "a",
        exactShareMinor: Long = 1000,
        currency: String = "EUR",
    ) = """
        {
          "format": "$format",
          "version": $version,
          "group": { "name": "G", "baseCurrency": "$baseCurrency" },
          "members": [ { "id": "a", "name": "A" }, { "id": "b", "name": "B" } ],
          "entries": [ {
            "id": "e", "kind": "EXPENSE", "title": "Lunch", "currency": "$currency", "rateToBase": 1.0,
            "spentAt": 0, "tzOffsetMinutes": 0, "categoryId": null, "note": null,
            "payments": [ { "member": "$payer", "amountMinor": 1000 } ],
            "split": { "type": "exact", "amounts": { "b": $exactShareMinor } },
            "futureField": true
          } ]
        }
        """.trimIndent()

    @Test
    fun hand_written_document_with_unknown_fields_imports() {
        val group = GroupJson.decode(document())

        assertEquals(1, group.entries.size)
        assertEquals(Money(1000, eur), group.entries.single().shareOf(group.members.first { it.name == "B" }.id))
    }

    @Test
    fun rejects_invalid_documents() {
        val cases =
            mapOf(
                "{ not json" to "not a Quits group file",
                document(format = "something-else") to "not a Quits group file",
                document(version = GROUP_DOCUMENT_VERSION + 1) to "newer version",
                document(payer = "ghost") to "unknown member ghost",
                document(exactShareMinor = 999) to "Lunch",
                document(currency = "EU") to "Lunch",
                document(baseCurrency = "1") to "group",
            )
        cases.forEach { (text, expected) ->
            val error = assertFailsWith<GroupImportException> { GroupJson.decode(text) }
            assertContains(error.message.orEmpty(), expected)
        }
    }

    @Test
    fun export_file_name_is_filesystem_safe() {
        assertEquals("Trip to Rome.quits.json", exportFileName("Trip to Rome"))
        assertEquals("a_b_c.quits.json", exportFileName("a/b:c"))
        assertEquals("group.quits.json", exportFileName("  "))
    }
}
