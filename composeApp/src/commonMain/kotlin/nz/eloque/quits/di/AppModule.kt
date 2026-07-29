package nz.eloque.quits.di

import nz.eloque.quits.data.invite.InviteResolver
import nz.eloque.quits.data.invite.PendingInvite
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.ui.expense.ExpenseDetailViewModel
import nz.eloque.quits.ui.expense.ExpenseEditorViewModel
import nz.eloque.quits.ui.group.GroupDetailViewModel
import nz.eloque.quits.ui.group.MemberDetailViewModel
import nz.eloque.quits.ui.groups.GroupsViewModel
import nz.eloque.quits.ui.settings.SettingsViewModel
import nz.eloque.quits.ui.stats.StatsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule =
    module {
        singleOf(::PendingInvite)
        singleOf(::InviteResolver)
        viewModelOf(::GroupsViewModel)
        viewModelOf(::SettingsViewModel)
        viewModel { params -> GroupDetailViewModel(get(), get(), get(), params.get<GroupId>()) }
        viewModel { params -> ExpenseEditorViewModel(get(), get(), get(), params.get<GroupId>(), params.getOrNull<String>()) }
        viewModel { params -> ExpenseDetailViewModel(get(), get(), params.get<GroupId>(), params.get<ExpenseId>()) }
        viewModel { params -> MemberDetailViewModel(get(), get(), params.get<GroupId>(), params.get<MemberId>()) }
        viewModel { params -> StatsViewModel(get(), params.get<GroupId>()) }
    }

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(appModule, databaseModule, platformModule, repositoryModule, syncModule, fxModule)
    }
}
