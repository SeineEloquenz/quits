package nz.eloque.quits.ui.entry

import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.isIncome
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.detail_delete_expense
import nz.eloque.quits.resources.detail_delete_income
import nz.eloque.quits.resources.detail_edit_expense
import nz.eloque.quits.resources.detail_edit_income
import nz.eloque.quits.resources.detail_for_whom
import nz.eloque.quits.resources.detail_owed_by
import nz.eloque.quits.resources.detail_paid_by
import nz.eloque.quits.resources.detail_received_by
import nz.eloque.quits.resources.editor_expense_fallback_title
import nz.eloque.quits.resources.editor_income_fallback_title
import nz.eloque.quits.resources.editor_paid_by
import nz.eloque.quits.resources.editor_paid_by_hint
import nz.eloque.quits.resources.editor_paid_by_prompt
import nz.eloque.quits.resources.editor_received_by
import nz.eloque.quits.resources.editor_received_by_hint
import nz.eloque.quits.resources.editor_received_by_prompt
import nz.eloque.quits.resources.editor_save_changes
import nz.eloque.quits.resources.editor_save_expense
import nz.eloque.quits.resources.editor_save_income
import nz.eloque.quits.resources.editor_title_add
import nz.eloque.quits.resources.editor_title_add_income
import nz.eloque.quits.resources.editor_title_edit
import nz.eloque.quits.resources.editor_title_edit_income
import nz.eloque.quits.resources.expense_delete_body
import nz.eloque.quits.resources.income_delete_body
import org.jetbrains.compose.resources.StringResource

/*
 * Kind-aware user-facing copy for an entry (expense vs income), as extensions on [EntryKind] so the
 * wording branches live in one place: callers write `stringResource(kind.payerHeadingRes())` instead
 * of scattering `if (isIncome) … else …`. Each returns a [StringResource]; the caller resolves it.
 */

/** Editor screen title, e.g. "Add expense" / "Edit income". */
fun EntryKind.titleRes(editing: Boolean): StringResource =
    when {
        isIncome && editing -> Res.string.editor_title_edit_income
        isIncome -> Res.string.editor_title_add_income
        editing -> Res.string.editor_title_edit
        else -> Res.string.editor_title_add
    }

/** Save-button label: "Save changes" when editing, else "Save expense" / "Save income". */
fun EntryKind.saveActionRes(editing: Boolean): StringResource =
    when {
        editing -> Res.string.editor_save_changes
        isIncome -> Res.string.editor_save_income
        else -> Res.string.editor_save_expense
    }

/** Default title for an untitled entry ("Expense" / "Income"). */
fun EntryKind.fallbackTitleRes(): StringResource =
    if (isIncome) Res.string.editor_income_fallback_title else Res.string.editor_expense_fallback_title

/** Section heading for who paid / received ("Paid by" / "Received by"). */
fun EntryKind.payerHeadingRes(): StringResource = if (isIncome) Res.string.editor_received_by else Res.string.editor_paid_by

/** Prompt when nobody is selected yet ("Tap who paid" / "Tap who received it"). */
fun EntryKind.payerPromptRes(): StringResource = if (isIncome) Res.string.editor_received_by_prompt else Res.string.editor_paid_by_prompt

/** Single-payer hint ("%1$s paid %2$s" / "%1$s received %2$s"). */
fun EntryKind.payerHintRes(): StringResource = if (isIncome) Res.string.editor_received_by_hint else Res.string.editor_paid_by_hint

/** Activity-feed subtitle ("paid by %1$s" / "received by %1$s"). */
fun EntryKind.payerFeedRes(): StringResource = if (isIncome) Res.string.detail_received_by else Res.string.detail_paid_by

/** Detail heading for the share side ("Owed by" / "For whom"). */
fun EntryKind.beneficiaryHeadingRes(): StringResource = if (isIncome) Res.string.detail_for_whom else Res.string.detail_owed_by

/** Delete-confirmation body. */
fun EntryKind.deleteBodyRes(): StringResource = if (isIncome) Res.string.income_delete_body else Res.string.expense_delete_body

/** Edit-action content description ("Edit expense" / "Edit income"). */
fun EntryKind.editActionRes(): StringResource = if (isIncome) Res.string.detail_edit_income else Res.string.detail_edit_expense

/** Delete-action content description ("Delete expense" / "Delete income"). */
fun EntryKind.deleteActionRes(): StringResource = if (isIncome) Res.string.detail_delete_income else Res.string.detail_delete_expense
