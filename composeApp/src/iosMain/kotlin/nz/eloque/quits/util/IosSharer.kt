package nz.eloque.quits.util

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
// popoverPresentationController is an ObjC category member, so Kotlin/Native exposes it as an
// extension property that must be imported explicitly.
import platform.UIKit.popoverPresentationController

/** Presents a `UIActivityViewController` from the top-most view controller. */
class IosSharer : Sharer {
    override fun share(text: String) {
        var top = keyRootViewController() ?: return
        while (top.presentedViewController != null) {
            top = top.presentedViewController!!
        }
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

/** The active window's root controller, via the foreground scene. */
private fun keyRootViewController(): UIViewController? {
    val scene =
        UIApplication.sharedApplication.connectedScenes
            .firstOrNull { it is UIWindowScene } as? UIWindowScene
    return scene?.keyWindow?.rootViewController
}
