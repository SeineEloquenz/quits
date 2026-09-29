package nz.eloque.quits.ui.entry

import nz.eloque.quits.domain.Bill
import nz.eloque.quits.domain.JPY
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Split
import nz.eloque.quits.domain.mid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        assertEquals(Bill(listOf(Payment(a, yen(300))), Split.Equal(listOf(a, b, c))), valid.entry.bill)
    }

    @Test
    fun removing_a_payer_redistributes_the_equal_payment() {
        val state = form().withAmount("300").withPayerToggled(a).withPayerToggled(b).withoutMember(b)
        assertEquals(setOf(a), state.payerSelected)
        assertEquals(mapOf(a to "300"), state.paid)
        val valid = assertIs<EntryValidation.Valid>(state.validate())
        assertEquals(Split.Equal(listOf(a, c)), valid.entry.bill.split)
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
    fun renaming_keeps_the_member_id() {
        val state = form().withMemberRenamed(b, "Bea")
        assertEquals(MemberInput(b, "Bea"), state.members[1])
    }
}
