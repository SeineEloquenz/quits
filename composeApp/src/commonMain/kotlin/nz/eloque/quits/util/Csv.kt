@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.util

import kotlinx.datetime.toLocalDateTime
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.NumberFormatSymbols
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Fixed, locale-independent symbols for exported amounts
 */
private object CsvNumberFormat : NumberFormatSymbols {
    override fun decimalSeparator(): Char = '.'

    override fun groupingSeparator(): Char? = null
}

/** Escapes one field per RFC 4180: quote it when it holds a comma, quote, CR or LF; double any quote. */
private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

private fun two(value: Int): String = value.toString().padStart(2, '0')

/**
 * Renders this group's expenses as an RFC-4180 CSV (CRLF line endings), newest first. Date and time
 * are ISO, each rendered in the offset the expense was entered in, so they read as the enterer meant.
 */
fun Group.expensesToCsv(): String {
    val names = members.associate { it.id to it.name }
    val header = listOf("Date", "Time", "Title", "Category", "Amount", "Currency", "Paid by", "Note")
    val rows =
        expenses
            .sortedByDescending { it.spentAt }
            .map { expense ->
                val at = Instant.fromEpochMilliseconds(expense.spentAt).toLocalDateTime(offsetZone(expense.tzOffsetMinutes))
                val payer =
                    expense.payments
                        .map { names[it.payer] ?: "?" }
                        .distinct()
                        .joinToString(", ")
                listOf(
                    at.date.toString(),
                    "${two(at.hour)}:${two(at.minute)}",
                    expense.title,
                    expense.category.orEmpty(),
                    expense.total.toDecimalString(CsvNumberFormat),
                    expense.currency.code,
                    payer,
                    expense.note.orEmpty(),
                )
            }
    return (listOf(header) + rows).joinToString(separator = "\r\n", postfix = "\r\n") { fields ->
        fields.joinToString(",", transform = ::csvField)
    }
}

/** A filesystem-safe `.csv` name derived from a group name, e.g. "Trip to Rome" -> "Trip to Rome.csv". */
fun csvFileName(groupName: String): String {
    val cleaned =
        groupName
            .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim()
            .ifEmpty { "expenses" }
    return "$cleaned.csv"
}
