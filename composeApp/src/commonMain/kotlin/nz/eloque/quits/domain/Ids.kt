package nz.eloque.quits.domain

import kotlin.jvm.JvmInline

@JvmInline
value class GroupId(
    val value: String,
)

@JvmInline
value class MemberId(
    val value: String,
)

@JvmInline
value class ExpenseId(
    val value: String,
)

@JvmInline
value class SettlementId(
    val value: String,
)
