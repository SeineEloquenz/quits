@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.util

import kotlinx.datetime.toLocalDateTime
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.NumberFormatSymbols
import nz.eloque.quits.domain.isIncome
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Fixed, locale-independent symbols for exported amounts. */
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

/** Renders the group's entries as an RFC-4180 CSV (CRLF), newest first; dates/times are ISO in each entry's captured offset. [categoryName] resolves a category id (null → empty cell). */
fun Group.entriesToCsv(categoryName: (CategoryId) -> String? = { null }): String {
    val names = members.associate { it.id to it.name }
    val header = listOf("Date", "Time", "Title", "Type", "Category", "Amount", "Currency", "Paid / received by", "Note")
    val rows =
        entries
            .sortedByDescending { it.spentAt }
            .map { entry ->
                val at = Instant.fromEpochMilliseconds(entry.spentAt).toLocalDateTime(offsetZone(entry.tzOffsetMinutes))
                val payer =
                    entry.payments
                        .map { names[it.member] ?: "?" }
                        .distinct()
                        .joinToString(", ")
                listOf(
                    at.date.toString(),
                    "${two(at.hour)}:${two(at.minute)}",
                    entry.title,
                    if (entry.kind.isIncome) "Income" else "Expense",
                    entry.categoryId?.let(categoryName).orEmpty(),
                    entry.total.toDecimalString(CsvNumberFormat),
                    entry.currency.code,
                    payer,
                    entry.note.orEmpty(),
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
            .ifEmpty { "entries" }
    return "$cleaned.csv"
}
