package nz.eloque.quits.data.sync

/**
 * Where the relay lives. Default targets a relay on the dev host as seen from the Android emulator
 * (`10.0.2.2` is the host's loopback). [instanceSecret] is sent when the instance gates creation.
 */
data class SyncConfig(
    val baseUrl: String = "http://10.0.2.2:8080",
    val instanceSecret: String? = null,
)
