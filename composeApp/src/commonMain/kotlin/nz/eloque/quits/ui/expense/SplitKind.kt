package nz.eloque.quits.ui.expense

import androidx.compose.runtime.Composable
import nz.eloque.quits.domain.Split
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.editor_split_equal
import nz.eloque.quits.resources.editor_split_exact
import nz.eloque.quits.resources.editor_split_itemized
import nz.eloque.quits.resources.editor_split_percentage
import nz.eloque.quits.resources.editor_split_shares
import org.jetbrains.compose.resources.stringResource

/**
 * Which shape a [Split] takes. The UI needs a tag like this — mirroring [Split]'s variants — for
 * things a full, valid [Split] can't represent yet: which segment is selected in the editor's
 * picker before the person has entered a valid amount, or which case to render an icon/label for.
 */
enum class SplitKind { EQUAL, SHARES, PERCENTAGE, EXACT, ITEMIZED }

/** The [SplitKind] a concrete [Split] belongs to. */
fun Split.kind(): SplitKind =
    when (this) {
        is Split.Equal -> SplitKind.EQUAL
        is Split.Shares -> SplitKind.SHARES
        is Split.Percentage -> SplitKind.PERCENTAGE
        is Split.Exact -> SplitKind.EXACT
        is Split.Itemized -> SplitKind.ITEMIZED
    }

/** Human-readable label for a [SplitKind], shared by the editor and the detail screen. */
@Composable
fun SplitKind.label(): String =
    when (this) {
        SplitKind.EQUAL -> stringResource(Res.string.editor_split_equal)
        SplitKind.SHARES -> stringResource(Res.string.editor_split_shares)
        SplitKind.PERCENTAGE -> stringResource(Res.string.editor_split_percentage)
        SplitKind.EXACT -> stringResource(Res.string.editor_split_exact)
        SplitKind.ITEMIZED -> stringResource(Res.string.editor_split_itemized)
    }
