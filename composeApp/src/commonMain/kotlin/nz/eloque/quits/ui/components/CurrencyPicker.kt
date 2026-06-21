package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nz.eloque.compose_kit.input.SearchablePickerField
import nz.eloque.quits.domain.Currencies
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.search_currency
import org.jetbrains.compose.resources.stringResource

private fun label(currency: Currency): String = "${currency.code} — ${Currencies.displayName(currency)}"

/** A searchable ISO-4217 currency picker: the currency catalog over the generic [SearchablePickerField]. */
@Composable
fun CurrencyPicker(
    label: String,
    selected: Currency,
    onSelected: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchablePickerField(
        label = label,
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
