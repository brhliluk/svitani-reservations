package cz.svitaninymburk.projects.reservations.android.feature.events

import cz.svitaninymburk.projects.reservations.android.feature.events.detail.InstanceDetailViewModel
import cz.svitaninymburk.projects.reservations.android.feature.events.detail.SeriesDetailViewModel
import cz.svitaninymburk.projects.reservations.android.feature.events.list.EventsViewModel
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepository
import cz.svitaninymburk.projects.reservations.android.repository.event.EventsRepositoryImpl
import kotlin.uuid.Uuid
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val eventsModule = module {
    single<EventsRepository> { EventsRepositoryImpl(get(), get()) }
    viewModel { EventsViewModel(get()) }
    viewModel { params -> InstanceDetailViewModel(params.get<Uuid>(), get()) }
    viewModel { params -> SeriesDetailViewModel(params.get<Uuid>(), get()) }
}
