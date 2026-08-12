package nz.eloque.quits.ui.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.isIncome
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.category_accommodation
import nz.eloque.quits.resources.category_dining
import nz.eloque.quits.resources.category_entertainment
import nz.eloque.quits.resources.category_gifts
import nz.eloque.quits.resources.category_groceries
import nz.eloque.quits.resources.category_health
import nz.eloque.quits.resources.category_income_gift
import nz.eloque.quits.resources.category_income_other
import nz.eloque.quits.resources.category_income_refund
import nz.eloque.quits.resources.category_income_reimbursement
import nz.eloque.quits.resources.category_income_rental
import nz.eloque.quits.resources.category_income_salary
import nz.eloque.quits.resources.category_other
import nz.eloque.quits.resources.category_shopping
import nz.eloque.quits.resources.category_transport
import nz.eloque.quits.resources.category_travel
import nz.eloque.quits.resources.category_utilities
import org.jetbrains.compose.resources.StringResource

/** A built-in category. Preset ids are stable strings shared across app builds; [nameRes] localizes only the display. */
data class PresetCategory(
    val id: CategoryId,
    val nameRes: StringResource,
    val iconKey: String,
    val color: Long,
)

/** Curated ARGB colors (0xAARRGGBB) offered for categories; render via [categoryColor]. */
val CATEGORY_COLORS: List<Long> =
    listOf(
        0xFF4CAF50,
        0xFFFF9800,
        0xFF2196F3,
        0xFF9C27B0,
        0xFFE91E63,
        0xFF009688,
        0xFFFFC107,
        0xFFF44336,
        0xFF00BCD4,
        0xFF795548,
        0xFF607D8B,
        0xFF3F51B5,
    )

/** Stable icon keys offered in the custom-category picker; resolve via [categoryIcon]. */
val CATEGORY_ICON_KEYS: List<String> =
    listOf(
        "grocery",
        "restaurant",
        "fastfood",
        "car",
        "flight",
        "hotel",
        "movie",
        "shopping_bag",
        "cart",
        "bolt",
        "hospital",
        "gift",
        "pets",
        "games",
        "fitness",
        "coffee",
        "bar",
        "home",
        "school",
        "work",
        "celebration",
        "checkroom",
        "payments",
        "savings",
        "undo",
        "exchange",
        "category",
    )

private val ICONS: Map<String, ImageVector> =
    mapOf(
        "grocery" to Icons.Filled.LocalGroceryStore,
        "restaurant" to Icons.Filled.Restaurant,
        "fastfood" to Icons.Filled.Fastfood,
        "car" to Icons.Filled.DirectionsCar,
        "flight" to Icons.Filled.Flight,
        "hotel" to Icons.Filled.Hotel,
        "movie" to Icons.Filled.Movie,
        "shopping_bag" to Icons.Filled.ShoppingBag,
        "cart" to Icons.Filled.ShoppingCart,
        "bolt" to Icons.Filled.Bolt,
        "hospital" to Icons.Filled.LocalHospital,
        "gift" to Icons.Filled.CardGiftcard,
        "pets" to Icons.Filled.Pets,
        "games" to Icons.Filled.SportsEsports,
        "fitness" to Icons.Filled.FitnessCenter,
        "coffee" to Icons.Filled.LocalCafe,
        "bar" to Icons.Filled.LocalBar,
        "home" to Icons.Filled.Home,
        "school" to Icons.Filled.School,
        "work" to Icons.Filled.Work,
        "celebration" to Icons.Filled.Celebration,
        "checkroom" to Icons.Filled.Checkroom,
        "payments" to Icons.Filled.Payments,
        "savings" to Icons.Filled.Savings,
        "undo" to Icons.Filled.Undo,
        "exchange" to Icons.Filled.CurrencyExchange,
        "category" to Icons.Filled.Category,
    )

/** Resolves an icon key to a drawable; unknown keys (e.g. from a newer build) fall back to a generic icon. */
fun categoryIcon(key: String): ImageVector = ICONS[key] ?: Icons.Filled.Category

/** A human-readable label for an icon key, used as the accessibility content description in the icon picker. */
fun categoryIconLabel(key: String): String = key.replace('_', ' ')

/** Renders a stored ARGB [argb] color; the low 32 bits are the AARRGGBB pattern. */
fun categoryColor(argb: Long): Color = Color(argb.toInt())

val PRESET_CATEGORIES: List<PresetCategory> =
    listOf(
        PresetCategory(CategoryId("groceries"), Res.string.category_groceries, "grocery", 0xFF4CAF50),
        PresetCategory(CategoryId("dining"), Res.string.category_dining, "restaurant", 0xFFFF9800),
        PresetCategory(CategoryId("transport"), Res.string.category_transport, "car", 0xFF2196F3),
        PresetCategory(CategoryId("accommodation"), Res.string.category_accommodation, "hotel", 0xFF9C27B0),
        PresetCategory(CategoryId("entertainment"), Res.string.category_entertainment, "movie", 0xFFE91E63),
        PresetCategory(CategoryId("shopping"), Res.string.category_shopping, "shopping_bag", 0xFF009688),
        PresetCategory(CategoryId("utilities"), Res.string.category_utilities, "bolt", 0xFFFFC107),
        PresetCategory(CategoryId("health"), Res.string.category_health, "hospital", 0xFFF44336),
        PresetCategory(CategoryId("travel"), Res.string.category_travel, "flight", 0xFF00BCD4),
        PresetCategory(CategoryId("gifts"), Res.string.category_gifts, "gift", 0xFF3F51B5),
        PresetCategory(CategoryId("other"), Res.string.category_other, "category", 0xFF607D8B),
    )

/** Preset categories offered for income events (money in), distinct from the entry set. */
val INCOME_PRESET_CATEGORIES: List<PresetCategory> =
    listOf(
        PresetCategory(CategoryId("income_refund"), Res.string.category_income_refund, "undo", 0xFF009688),
        PresetCategory(CategoryId("income_reimbursement"), Res.string.category_income_reimbursement, "exchange", 0xFF3F51B5),
        PresetCategory(CategoryId("income_salary"), Res.string.category_income_salary, "payments", 0xFF4CAF50),
        PresetCategory(CategoryId("income_rental"), Res.string.category_income_rental, "home", 0xFF2196F3),
        PresetCategory(CategoryId("income_gift"), Res.string.category_income_gift, "gift", 0xFFE91E63),
        PresetCategory(CategoryId("income_other"), Res.string.category_income_other, "category", 0xFF607D8B),
    )

// Resolution recognizes every preset (entry + income) so a stored id renders correctly regardless
// of which editor created it; the editor still shows only the kind-appropriate list.
private val PRESET_BY_ID: Map<CategoryId, PresetCategory> =
    (PRESET_CATEGORIES + INCOME_PRESET_CATEGORIES).associateBy { it.id }

fun presetCategory(id: CategoryId): PresetCategory? = PRESET_BY_ID[id]

/** Preset ids known to this build; used to tell an unresolvable id (newer build) from a known one. */
val PRESET_IDS: Set<CategoryId> = PRESET_BY_ID.keys

/** The preset list to offer for [kind]'s editor. */
fun presetsFor(kind: EntryKind): List<PresetCategory> = if (kind.isIncome) INCOME_PRESET_CATEGORIES else PRESET_CATEGORIES
