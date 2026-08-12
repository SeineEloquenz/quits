package nz.eloque.quits.data.invite

import nz.eloque.quits.data.crypto.SecretCode

/**
 * Builds and parses shareable invite links. The group secret ([SecretCode]) is stored in the URL
 * **fragment** (`https://quits.eloque.nz/join#<code>`) which is never sent in HTTP requests.
 */
object InviteLink {
    const val HOST = "quits.eloque.nz"
    const val PATH = "/join"
    private const val BASE = "https://$HOST$PATH"

    /** A shareable invite URL carrying [code] in the fragment. */
    fun build(code: String): String = "$BASE#$code"

    /** Extracts the share code from an invite [url] (or deep link), or null if it carries no valid code. */
    fun parse(url: String): String? {
        val fragment = url.substringAfter('#', "").trim()
        if (fragment.isEmpty()) return null
        return if (SecretCode.decode(fragment) != null) fragment else null
    }
}
