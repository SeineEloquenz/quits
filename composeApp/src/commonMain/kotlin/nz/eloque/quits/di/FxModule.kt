package nz.eloque.quits.di

import nz.eloque.quits.data.fx.FrankfurterFxRateProvider
import nz.eloque.quits.domain.FxRateProvider
import org.koin.dsl.module

val fxModule =
    module {
        single<FxRateProvider> { FrankfurterFxRateProvider(get()) }
    }
