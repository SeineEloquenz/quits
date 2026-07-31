package nz.eloque.quits.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Split
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class CsvTest {
    private val usd = Currency.of("USD")
    private val a = MemberId("a")
    private val b = MemberId("b")
    private val members = listOf(Member(a, "Alice"), Member(b, "Bob"))

    @OptIn(ExperimentalTime::class)
    private fun at(iso: String): Long = LocalDateTime.parse(iso).toInstant(TimeZone.UTC).toEpochMilliseconds()

    private fun group(vararg expenses: Expense) = Group(GroupId("g"), "Trip", usd, members, expenses.toList())

    @Test
    fun renders_header_and_rows_newest_first() {
        val older =
            Expense(
                ExpenseId("e1"),
                "Dinner",
                listOf(Payment(a, Money(3000, usd))),
                Split.Equal(listOf(a, b)),
                spentAt = at("2026-07-28T19:30"),
                categoryId = CategoryId("food"),
            )
        val newer =
            Expense(
                ExpenseId("e2"),
                "Taxi",
                listOf(Payment(b, Money(1250, usd))),
                Split.Equal(listOf(a, b)),
                spentAt = at("2026-07-29T08:05"),
            )
        val csv = group(older, newer).expensesToCsv { if (it == CategoryId("food")) "Food" else null }

        assertEquals(
            "Date,Time,Title,Category,Amount,Currency,Paid by,Note\r\n" +
                "2026-07-29,08:05,Taxi,,12.50,USD,Bob,\r\n" +
                "2026-07-28,19:30,Dinner,Food,30.00,USD,Alice,\r\n",
            csv,
        )
    }

    @Test
    fun escapes_commas_quotes_and_newlines_per_rfc4180() {
        val expense =
            Expense(
                ExpenseId("e1"),
                "Lunch, deluxe",
                listOf(Payment(a, Money(1000, usd))),
                Split.Equal(listOf(a, b)),
                spentAt = at("2026-07-29T12:00"),
                note = "say \"hi\"\nsecond line",
            )
        val csv = group(expense).expensesToCsv()

        assertEquals(
            "Date,Time,Title,Category,Amount,Currency,Paid by,Note\r\n" +
                "2026-07-29,12:00,\"Lunch, deluxe\",,10.00,USD,Alice,\"say \"\"hi\"\"\nsecond line\"\r\n",
            csv,
        )
    }

    @Test
    fun amount_uses_dot_decimal_regardless_of_locale() {
        // A JPY (0-decimal) amount and a 2-decimal USD amount both render machine-parseable.
        val jpy = Currency.of("JPY")
        val expense =
            Expense(
                ExpenseId("e1"),
                "Sushi",
                listOf(Payment(a, Money(1500, jpy))),
                Split.Equal(listOf(a, b)),
                spentAt = at("2026-07-29T12:00"),
            )
        val group = Group(GroupId("g"), "Trip", jpy, members, listOf(expense))
        val line = group.expensesToCsv().lines()[1]

        assertEquals("2026-07-29,12:00,Sushi,,1500,JPY,Alice,", line)
    }

    @Test
    fun date_and_time_render_in_the_captured_offset() {
        // 23:30 UTC with a +60 min offset is 00:30 the next day where it was entered.
        val expense =
            Expense(
                ExpenseId("e1"),
                "Late snack",
                listOf(Payment(a, Money(500, usd))),
                Split.Equal(listOf(a, b)),
                spentAt = at("2026-07-29T23:30"),
                tzOffsetMinutes = 60,
            )
        val line = group(expense).expensesToCsv().lines()[1]

        assertEquals("2026-07-30,00:30,Late snack,,5.00,USD,Alice,", line)
    }

    @Test
    fun file_name_is_sanitized() {
        assertEquals("Trip to Rome.csv", csvFileName("Trip to Rome"))
        assertEquals("Flat_share 2026.csv", csvFileName("Flat/share 2026"))
        assertEquals("expenses.csv", csvFileName("   "))
    }
}
