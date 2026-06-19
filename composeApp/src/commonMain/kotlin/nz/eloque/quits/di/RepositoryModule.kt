package nz.eloque.quits.di

import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.util.nowMillis
import org.koin.dsl.module

val repositoryModule =
    module {
        single {
            GroupRepository(
                db = get(),
                deviceId = get<DeviceId>().value,
                now = { nowMillis() },
            )
        }
    }
