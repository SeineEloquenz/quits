package nz.eloque.quits.di

import nz.eloque.quits.data.db.QuitsDatabase
import nz.eloque.quits.data.fx.CachingFxRateProvider
import nz.eloque.quits.data.fx.FrankfurterFxRateProvider
import nz.eloque.quits.data.fx.FxRates
import nz.eloque.quits.util.nowMillis
import org.koin.dsl.module

val fxModule =
    module {
        single<FxRates> {
            CachingFxRateProvider(
                delegate = FrankfurterFxRateProvider(get()),
                dao = get<QuitsDatabase>().fxRateDao(),
                now = { nowMillis() },
            )
        }
    }
