package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import nz.eloque.quits.util.topViewController
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

@Composable
actual fun rememberTextFilePicker(
    mimeType: String,
    onPicked: (String) -> Unit,
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    // UIDocumentPickerViewController holds its delegate weakly, so it must be retained here.
    val delegate = remember { TextFilePickerDelegate { currentOnPicked(it) } }
    return remember(mimeType, delegate) {
        {
            val type = UTType.typeWithMIMEType(mimeType) ?: UTTypeData
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(type), asCopy = true)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class TextFilePickerDelegate(
    private val onText: (String) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val text = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null) ?: return
        onText(text)
    }
}
