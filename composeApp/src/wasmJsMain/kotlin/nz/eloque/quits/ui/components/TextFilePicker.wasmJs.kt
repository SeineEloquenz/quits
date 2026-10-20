package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun rememberTextFilePicker(
    mimeType: String,
    onPicked: (String) -> Unit,
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    return remember(mimeType) { { pickTextFile(mimeType) { currentOnPicked(it) } } }
}

private fun pickTextFile(
    mimeType: String,
    onText: (String) -> Unit,
) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = mimeType
    input.onchange = {
        input.files?.get(0)?.let { file ->
            val reader = FileReader()
            reader.onload = { reader.result?.toString()?.let(onText) }
            reader.readAsText(file)
        }
    }
    input.click()
}
