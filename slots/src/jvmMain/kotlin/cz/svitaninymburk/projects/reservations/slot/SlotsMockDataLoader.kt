package cz.svitaninymburk.projects.reservations.slot

import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.SlotServiceRepository
import cz.svitaninymburk.projects.reservations.util.nowInAppTimeZone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** Ukázková služba hlídání pro `LOAD_MOCK_DATA`: út a čt 8–12, příští čtvrtek zavřeno. */
class SlotsMockDataLoader : KoinComponent {
    private val services: SlotServiceRepository by inject()
    private val rules: AvailabilityRuleRepository by inject()
    private val exceptions: AvailabilityExceptionRepository by inject()

    suspend fun load() {
        services.getAll().forEach { services.delete(it.id) }

        val today = nowInAppTimeZone().date
        val service = services.create(
            SlotService(
                id = Uuid.random(),
                title = "Hlídání dětí",
                description = "Pohlídáme vaše děti v herně Svítání, zatímco si vyřídíte, co potřebujete.",
                step = 30.minutes,
                // Výchozí jako u nové služby z adminu: bez minima a bez limitu délky.
                minDuration = Duration.ZERO,
                maxDuration = null,
                pricePerHour = 150.0,
                capacity = 8,
                maxSeatsPerBooking = 3,
                customFields = listOf(
                    TextFieldDefinition(key = "children", label = "Jména a věk dětí", isRequired = true, isMultiline = true),
                    TextFieldDefinition(key = "note", label = "Poznámka (např. kdy dítě přivedete a vyzvednete)", isMultiline = true),
                ),
                reservationDeadline = 2.hours,
                bookingHorizon = 28.days,
                // Zatím jen pro adminy, viz docs/hlidani/rozhodnuti.md.
                isPublished = false,
            )
        )
        rules.replaceForService(
            service.id,
            listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY).map { day ->
                AvailabilityRule(Uuid.random(), service.id, day, LocalTime(8, 0), LocalTime(12, 0), validFrom = today)
            },
        )
        var nextThursday = today.plus(DatePeriod(days = 1))
        while (nextThursday.dayOfWeek != DayOfWeek.THURSDAY) nextThursday = nextThursday.plus(DatePeriod(days = 1))
        exceptions.save(AvailabilityException(Uuid.random(), service.id, nextThursday, closed = true))
    }
}
