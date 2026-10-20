package nz.eloque.quits.util

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene

/** The top-most presented controller of the foreground scene's key window. */
internal fun topViewController(): UIViewController? {
    val scene =
        UIApplication.sharedApplication.connectedScenes
            .firstOrNull { it is UIWindowScene } as? UIWindowScene
    var top = scene?.keyWindow?.rootViewController ?: return null
    while (top.presentedViewController != null) {
        top = top.presentedViewController!!
    }
    return top
}
