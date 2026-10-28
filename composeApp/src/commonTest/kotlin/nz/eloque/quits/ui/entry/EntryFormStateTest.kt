package nz.eloque.quits.ui.entry

import nz.eloque.quits.domain.Bill
import nz.eloque.quits.domain.DefaultSplit
import nz.eloque.quits.domain.JPY
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Split
import nz.eloque.quits.domain.mid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryFormStateTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")

    private fun yen(amount: Long) = Money(amount, JPY)

    private fun form() =
        EntryFormState(baseCurrency = JPY, currency = JPY)
            .withMember(MemberInput(a, "A"))
            .withMember(MemberInput(b, "B"))
            .withMember(MemberInput(c, "C"))

    @Test
    fun equal_split_validates_into_a_bill() {
        val state = form().withAmount("300").withPayerToggled(a)
        val valid = assertIs<EntryValidation.Valid>(state.validate())
        assertEquals(Bill(listOf(Payment(a, yen(300))), Split.Equal(listOf(a, b, c))), valid.bill)
    }

    @Test
    fun removing_a_payer_redistributes_the_equal_payment() {
        val state = form().withAmount("300").withPayerToggled(a).withPayerToggled(b).withoutMember(b)
        assertEquals(setOf(a), state.payerSelected)
        assertEquals(mapOf(a to "300"), state.paid)
        val valid = assertIs<EntryValidation.Valid>(state.validate())
        assertEquals(Split.Equal(listOf(a, c)), valid.bill.split)
    }

    @Test
    fun removing_a_member_clears_split_inputs() {
        val state =
            form()
                .withSplitKind(SplitKind.SHARES)
                .copy(splitInput = mapOf(a to "1", b to "2"))
                .withoutMember(b)
        assertEquals(mapOf(a to "1"), state.splitInput)
        assertEquals(listOf(a, c), state.members.map { it.id })
        assertEquals(setOf(a, c), state.equalSelected)
    }

    @Test
    fun removing_a_member_drops_their_sole_items_and_retotals() {
        val state =
            form()
                .withSplitKind(SplitKind.ITEMIZED)
                .withPayerToggled(a)
                .copy(draftLabel = "Beer", draftAmount = "100", draftParticipants = setOf(b))
                .withDraftSubmitted()
                .copy(draftLabel = "Pizza", draftAmount = "200", draftParticipants = setOf(a, b))
                .withDraftSubmitted()
                .copy(draftParticipants = setOf(b, c))
                .withoutMember(b)
        assertEquals(listOf("Pizza"), state.items.map { it.label })
        assertEquals(setOf(a), state.items.single().participants)
        assertEquals("200", state.amount)
        assertEquals(mapOf(a to "200"), state.paid)
        assertEquals(setOf(c), state.draftParticipants)
        assertTrue(state.validate() is EntryValidation.Valid)
    }

    @Test
    fun switching_kind_seeds_from_the_equal_selection() {
        val base = form().withEqualToggled(c)
        assertEquals(mapOf(a to "1", b to "1"), base.withSplitKind(SplitKind.SHARES).splitInput)
        val thirds = form().withSplitKind(SplitKind.PERCENTAGE).splitInput
        assertEquals(mapOf(a to "34", b to "33", c to "33"), thirds)
        assertEquals(setOf(a, b), base.withSplitKind(SplitKind.ITEMIZED).draftParticipants)
    }

    @Test
    fun switching_back_restores_entered_inputs() {
        val state =
            form()
                .withSplitKind(SplitKind.SHARES)
                .copy(splitInput = mapOf(a to "3", b to "1"))
                .withSplitKind(SplitKind.EXACT)
                .withSplitKind(SplitKind.SHARES)
        assertEquals(mapOf(a to "3", b to "1"), state.splitInput)
    }

    @Test
    fun filling_the_exact_remainder_splits_it_across_empty_fields() {
        val state =
            form()
                .withAmount("1000")
                .withSplitKind(SplitKind.EXACT)
                .copy(splitInput = mapOf(a to "400"))
                .withExactRemainderFilled()
        assertEquals(mapOf(a to "400", b to "300", c to "300"), state.splitInput)
        assertTrue(state.remaining(state.splitInput)!!.isZero)
    }

    @Test
    fun untouched_seeds_follow_a_changed_equal_selection() {
        val state =
            form()
                .withSplitKind(SplitKind.SHARES)
                .withSplitKind(SplitKind.EQUAL)
                .withEqualToggled(c)
                .withSplitKind(SplitKind.SHARES)
        assertEquals(mapOf(a to "1", b to "1"), state.splitInput)
    }

    @Test
    fun editing_an_item_moves_it_back_into_the_draft() {
        val state =
            form()
                .withSplitKind(SplitKind.ITEMIZED)
                .copy(draftLabel = "Beer", draftAmount = "100", draftParticipants = setOf(b))
                .withDraftSubmitted()
        assertEquals(setOf(b), state.draftParticipants)
        val edited = state.withItemEdited(state.items.single().id)
        assertEquals(emptyList(), edited.items)
        assertEquals("Beer", edited.draftLabel)
        assertEquals("", edited.amount)
    }

    @Test
    fun renaming_keeps_the_member_id() {
        val state = form().withMemberRenamed(b, "Bea")
        assertEquals(MemberInput(b, "Bea"), state.members[1])
    }

    @Test
    fun split_inputs_become_a_default() {
        val shares = form().withSplitKind(SplitKind.SHARES).copy(splitInput = mapOf(a to "2", b to "1"))
        assertEquals(DefaultSplit(DefaultSplit.Kind.SHARES, mapOf(a to 2L, b to 1L, c to 0L)), shares.toDefaultSplit())
        assertNull(form().withSplitKind(SplitKind.PERCENTAGE).copy(splitInput = mapOf(a to "50")).toDefaultSplit())
        assertNull(form().withSplitKind(SplitKind.EXACT).toDefaultSplit())
    }

    @Test
    fun a_form_loaded_from_a_default_matches_it() {
        val members = listOf(a, b, c)
        listOf(
            DefaultSplit(DefaultSplit.Kind.EQUAL, mapOf(a to 1L, b to 0L)),
            DefaultSplit(DefaultSplit.Kind.SHARES, mapOf(a to 3L)),
            DefaultSplit(DefaultSplit.Kind.PERCENTAGE, mapOf(a to 50L, b to 50L, c to 0L)),
        ).forEach { default ->
            val split = default.splitFor(members)
            assertEquals(DefaultSplit.from(split, members), form().withSplit(split).toDefaultSplit())
        }
    }
}
