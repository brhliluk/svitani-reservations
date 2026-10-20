package cz.svitaninymburk.projects.reservations.slot

import cz.svitaninymburk.projects.reservations.ServerFeature
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.ExposedAvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.ExposedAvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.ExposedSlotServiceRepository
import cz.svitaninymburk.projects.reservations.slot.repository.SLOT_TABLES
import cz.svitaninymburk.projects.reservations.slot.repository.SlotServiceRepository
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.service.AdminDashboardService
import cz.svitaninymburk.projects.reservations.service.ReservationService
import cz.svitaninymburk.projects.reservations.service.SlotBookingProvider
import dev.kilua.rpc.applyRoutes
import org.koin.dsl.bind
import org.koin.dsl.module

val slotsKoinModule = module {
    single<SlotServiceRepository> { ExposedSlotServiceRepository() }
    single<AvailabilityRuleRepository> { ExposedAvailabilityRuleRepository() }
    single<AvailabilityExceptionRepository> { ExposedAvailabilityExceptionRepository() }
    // Jádro se přes SlotBookingProvider ptá na cíl rezervace úseku.
    single { SlotAvailability(get(), get(), get(), get()) } bind SlotBookingProvider::class
    single { SlotBookingService(get(), get(), get<ReservationService>()) } bind SlotServiceInterface::class
    single {
        val admin = get<AdminDashboardService>()
        SlotAdminService(
            services = get(),
            rules = get(),
            exceptions = get(),
            availability = get(),
            reservations = get(),
            audit = get(),
            canceller = DeletedTargetCanceller(admin::cancelReservationsOfDeletedTarget),
        )
    } bind SlotAdminServiceInterface::class
}

/** Hlídání dětí, jak ho do serveru zapojí aplikace (`Features.kt`). */
val slotsServerFeature = ServerFeature(
    name = "slots",
    tables = SLOT_TABLES,
    koinModules = listOf(slotsKoinModule),
    loadMockData = { SlotsMockDataLoader().load() },
    // Vygenerovaný manager napřímo: getServiceManager() z jádra o službách modulu neví.
    publicRoutes = { applyRoutes(SlotServiceInterfaceManager, RpcSerializersModules) },
    adminRoutes = { applyRoutes(SlotAdminServiceInterfaceManager, RpcSerializersModules) },
)
