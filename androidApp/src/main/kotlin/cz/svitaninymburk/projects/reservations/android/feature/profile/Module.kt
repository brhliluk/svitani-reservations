package cz.svitaninymburk.projects.reservations.android.feature.profile

import cz.svitaninymburk.projects.reservations.android.repository.wallet.WalletRepository
import cz.svitaninymburk.projects.reservations.android.repository.wallet.WalletRepositoryImpl
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    single<WalletRepository> { WalletRepositoryImpl(get(), get()) }
    viewModel { ProfileViewModel(get(), get()) }
}
