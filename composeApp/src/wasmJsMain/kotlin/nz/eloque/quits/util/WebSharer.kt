package nz.eloque.quits.util

/** Uses the Web Share API when available, else copies to the clipboard. */
class WebSharer : Sharer {
    override fun share(text: String) {
        if (hasWebShare()) webShare(text) else clipboardWrite(text)
    }
}

private fun hasWebShare(): Boolean = js("typeof navigator.share === 'function'")

private fun webShare(text: String) {
    js("navigator.share({ text: text }).catch(function(){})")
}

private fun clipboardWrite(text: String) {
    js("navigator.clipboard && navigator.clipboard.writeText(text)")
}
