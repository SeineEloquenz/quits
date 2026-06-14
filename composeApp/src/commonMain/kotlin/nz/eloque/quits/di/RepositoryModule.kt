package nz.eloque.quits.di

import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.util.nowMillis
import org.koin.dsl.module

val repositoryModule =
    module {
        single {
            // deviceId is a fixed placeholder until sync lands (it only matters for LWW tiebreaks).
            GroupRepository(
                db = get(),
                deviceId = "local-device",
                now = { nowMillis() },
            )
        }
    }
