package nz.eloque.quits.di

import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.ui.expense.ExpenseEditorViewModel
import nz.eloque.quits.ui.group.GroupDetailViewModel
import nz.eloque.quits.ui.groups.GroupsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule =
    module {
        viewModelOf(::GroupsViewModel)
        viewModel { params -> GroupDetailViewModel(get(), params.get<GroupId>()) }
        viewModel { params -> ExpenseEditorViewModel(get(), params.get<GroupId>(), params.getOrNull<String>()) }
    }

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(appModule, databaseModule, platformModule, repositoryModule, syncModule)
    }
}
