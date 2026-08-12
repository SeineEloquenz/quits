package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import nz.eloque.quits.domain.Currencies
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.search_currency
import org.jetbrains.compose.resources.stringResource

private fun label(currency: Currency): String = "${currency.code} — ${Currencies.displayName(currency)}"

/** The [ListPickerRow]-styled currency picker (searchable ISO-4217 catalog), for use in a [ListFieldCard]. */
@Composable
fun CurrencyPickerRow(
    icon: ImageVector,
    fieldLabel: String,
    selected: Currency,
    onSelected: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchablePickerRow(
        icon = icon,
        label = fieldLabel,
        selected = selected,
        selectedLabel = ::label,
        onSelected = onSelected,
        search = { Currencies.search(it) },
        itemKey = { it.code },
        itemLabel = ::label,
        searchLabel = stringResource(Res.string.search_currency),
        modifier = modifier,
    )
}
