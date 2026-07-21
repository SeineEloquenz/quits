package nz.eloque.quits.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import nz.eloque.quits.domain.Money

/** Human-readable amount, e.g. "19.99 USD". */
fun Money.display(): String = "${toDecimalString()} ${currency.code}"

/** A plain amount with no balance semantics. */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(money.display(), modifier = modifier, style = style)
}

/**
 * A net balance, colored by sign: owed-to-them (positive) in primary, owing (negative) in error,
 * settled (zero) muted.
 */
@Composable
fun BalanceText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Text(money.display(), modifier = modifier, color = money.balanceColor(), style = style, fontWeight = FontWeight.Medium)
}

@Composable
private fun Money.balanceColor(): Color =
    when {
        this.isPositive -> balanceGreen()
        this.isNegative -> balanceRed()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun balanceGreen(): Color =
    if (isSystemInDarkTheme()) {
        Color(0xFF81C784)
    } else {
        Color(0xFF388E3C)
    }

@Composable
private fun balanceRed(): Color =
    if (isSystemInDarkTheme()) {
        Color(0xFFE57373)
    } else {
        Color(0xFFD32F2F)
    }
