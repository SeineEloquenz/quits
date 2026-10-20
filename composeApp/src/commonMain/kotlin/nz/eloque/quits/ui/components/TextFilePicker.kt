package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable

/** Returns a launcher that lets the user pick a file of [mimeType] and hands its text to [onPicked]. Cancelling does nothing. */
@Composable
expect fun rememberTextFilePicker(
    mimeType: String,
    onPicked: (String) -> Unit,
): () -> Unit
