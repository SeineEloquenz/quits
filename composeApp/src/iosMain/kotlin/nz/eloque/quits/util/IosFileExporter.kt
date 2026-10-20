package nz.eloque.quits.util

import nz.eloque.quits.application.transfer.FileExporter

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
// popoverPresentationController is an ObjC category member, so Kotlin/Native exposes it as an
// extension property that must be imported explicitly.
import platform.UIKit.popoverPresentationController

/** Writes the file to the temp directory and shares its URL via a `UIActivityViewController`. */
class IosFileExporter : FileExporter {
    @OptIn(ExperimentalForeignApi::class)
    override fun export(
        fileName: String,
        mimeType: String,
        content: String,
    ) {
        val path = (NSTemporaryDirectory() as NSString).stringByAppendingPathComponent(fileName)
        val url = NSURL.fileURLWithPath(path)
        (content as NSString).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)

        val top = topViewController() ?: return
        val activity =
            UIActivityViewController(
                activityItems = listOf(url),
                applicationActivities = null,
            )
        // Anchor the popover (required on iPad) so presentation never crashes.
        activity.popoverPresentationController?.sourceView = top.view
        top.presentViewController(activity, animated = true, completion = null)
    }
}
