package nz.eloque.quits.ui.category

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import org.jetbrains.compose.resources.stringResource

/** A category resolved to what the UI shows: localized/custom [name], [icon] and [color]. */
data class CategoryDisplay(
    val id: CategoryId,
    val name: String,
    val icon: ImageVector,
    val color: Color,
)

/**
 * Resolves [id] to its display — custom [categories] first, then app presets. Returns null when the
 * expense is uncategorized ([id] null) or the id is unknown to this build (a newer preset, or a
 * custom category not yet synced): such expenses read as uncategorized until resolvable.
 */
@Composable
fun categoryDisplay(
    id: CategoryId?,
    categories: List<Category>,
): CategoryDisplay? {
    if (id == null) return null
    categories.firstOrNull { it.id == id }?.let {
        return CategoryDisplay(id, it.name, categoryIcon(it.icon), categoryColor(it.color))
    }
    presetCategory(id)?.let {
        return CategoryDisplay(id, stringResource(it.nameRes), categoryIcon(it.iconKey), categoryColor(it.color))
    }
    return null
}

/** Compact icon + name chip tinted with the category's color, for the activity feed and detail. */
@Composable
fun CategoryPill(
    display: CategoryDisplay,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.small, color = display.color.copy(alpha = 0.15f), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(display.icon, contentDescription = null, tint = display.color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                display.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
