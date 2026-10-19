package cz.svitaninymburk.projects.reservations.slot

import cz.svitaninymburk.projects.reservations.ServerFeature
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.ExposedAvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.ExposedAvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.ExposedSlotServiceRepository
import cz.svitaninymburk.projects.reservations.slot.repository.SLOT_TABLES
import cz.svitaninymburk.projects.reservations.slot.repository.SlotServiceRepository
import org.koin.dsl.module

val slotsKoinModule = module {
    single<SlotServiceRepository> { ExposedSlotServiceRepository() }
    single<AvailabilityRuleRepository> { ExposedAvailabilityRuleRepository() }
    single<AvailabilityExceptionRepository> { ExposedAvailabilityExceptionRepository() }
}

/** Hlídání dětí, jak ho do serveru zapojí aplikace (`Features.kt`). */
val slotsServerFeature = ServerFeature(
    name = "slots",
    tables = SLOT_TABLES,
    koinModules = listOf(slotsKoinModule),
    loadMockData = { SlotsMockDataLoader().load() },
)
