package cz.svitaninymburk.projects.reservations.android.feature.reservations

import cz.svitaninymburk.projects.reservations.android.feature.reservations.detail.ReservationDetailViewModel
import cz.svitaninymburk.projects.reservations.android.feature.reservations.list.ReservationsViewModel
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepository
import cz.svitaninymburk.projects.reservations.android.repository.reservation.ReservationsRepositoryImpl
import cz.svitaninymburk.projects.reservations.reservation.MyReservationListItem
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reservationsModule = module {
    single<ReservationsRepository> { ReservationsRepositoryImpl(get(), get()) }
    viewModel { ReservationsViewModel(get()) }
    viewModel { params -> ReservationDetailViewModel(params.get<MyReservationListItem>(), get()) }
}
