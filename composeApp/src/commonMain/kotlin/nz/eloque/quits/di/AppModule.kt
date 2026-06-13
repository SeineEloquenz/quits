package nz.eloque.quits.di

import nz.eloque.quits.ui.groups.GroupsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule =
    module {
        viewModelOf(::GroupsViewModel)
    }

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(appModule, databaseModule, platformModule)
    }
}
