package nz.eloque.quits.util

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/** Presents a `UIActivityViewController` from the top-most view controller. */
class IosSharer : Sharer {
    override fun share(text: String) {
        val activity =
            UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        while (top.presentedViewController != null) {
            top = top.presentedViewController!!
        }
        // Anchor the popover (required on iPad) so presentation never crashes.
        activity.popoverPresentationController?.sourceView = top.view
        top.presentViewController(activity, animated = true, completion = null)
    }
}
