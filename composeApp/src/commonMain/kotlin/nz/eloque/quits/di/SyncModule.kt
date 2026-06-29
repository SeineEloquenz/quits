package nz.eloque.quits.di

import com.russhwolf.settings.Settings
import nz.eloque.quits.data.crypto.GroupCrypto
import nz.eloque.quits.data.sync.PersistentSyncSettings
import nz.eloque.quits.data.sync.Relay
import nz.eloque.quits.data.sync.RelayClient
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.data.sync.SyncSettings
import nz.eloque.quits.util.newId
import nz.eloque.quits.util.nowMillis
import org.koin.dsl.module

/** A stable, per-install device id (LWW tiebreak), generated once and persisted in settings. */
data class DeviceId(
    val value: String,
)

private const val DEVICE_ID_KEY = "device_id"

private fun resolveDeviceId(settings: Settings): String =
    settings.getStringOrNull(DEVICE_ID_KEY) ?: newId().also { settings.putString(DEVICE_ID_KEY, it) }

val syncModule =
    module {
        single { DeviceId(resolveDeviceId(get())) }
        single<SyncSettings> { PersistentSyncSettings(get()) }
        single<Relay> { RelayClient(get(), get()) }
        single { GroupCrypto() }
        single { SyncEngine(get(), get(), get(), get<DeviceId>().value, now = { nowMillis() }) }
    }
