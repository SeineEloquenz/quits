package nz.eloque.quits.util

import platform.UIKit.UIActivityViewController
// popoverPresentationController is an ObjC category member, so Kotlin/Native exposes it as an
// extension property that must be imported explicitly.
import platform.UIKit.popoverPresentationController

/** Presents a `UIActivityViewController` from the top-most view controller. */
class IosSharer : Sharer {
    override fun share(text: String) {
        val top = topViewController() ?: return
        val activity =
            UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
        // Anchor the popover (required on iPad) so presentation never crashes.
        activity.popoverPresentationController?.sourceView = top.view
        top.presentViewController(activity, animated = true, completion = null)
    }
}
