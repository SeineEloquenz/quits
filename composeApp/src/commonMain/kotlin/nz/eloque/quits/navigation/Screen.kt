package nz.eloque.quits.navigation

sealed interface Screen {
    data object Groups : Screen

    data class GroupDetail(
        val name: String,
    ) : Screen
}
