package cz.svitaninymburk.projects.reservations.slot

import cz.svitaninymburk.projects.reservations.repository.reservation.ReservationRepository
import cz.svitaninymburk.projects.reservations.reservation.Reference
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.SlotBookingProvider
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityExceptionRepository
import cz.svitaninymburk.projects.reservations.slot.repository.AvailabilityRuleRepository
import cz.svitaninymburk.projects.reservations.slot.repository.SlotServiceRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * Obsazenost služeb spočítaná z pravidel, výjimek a aktivních rezervací — nic z toho
 * se neukládá. Zároveň dává jádru cíl rezervace úseku ([SlotBookingProvider]).
 *
 * Záměrně nezávisí na rezervační službě jádra: ta přes resolver potřebuje tuhle
 * třídu, takže by vznikl cyklus.
 */
class SlotAvailability(
    private val services: SlotServiceRepository,
    private val rules: AvailabilityRuleRepository,
    private val exceptions: AvailabilityExceptionRepository,
    private val reservations: ReservationRepository,
) : SlotBookingProvider {

    /** Dny [from]–[to] s kroky a volnými místy; dny bez okna ve výsledku nejsou. */
    suspend fun days(service: SlotService, from: LocalDate, to: LocalDate): List<DayAvailability> {
        val booked = reservations
            .findActiveSlotReservations(service.id, from.atStartOfDay(), to.plus(1, DateTimeUnit.DAY).atStartOfDay())
            .mapNotNull { reservation ->
                (reservation.reference as? Reference.Slot)?.let { BookedInterval(it.start, it.end, reservation.seatCount) }
            }
        return expandAvailability(
            service = service,
            rules = rules.findByService(service.id),
            exceptions = exceptions.findByService(service.id, from, to),
            bookedIntervals = booked,
            from = from,
            to = to,
        )
    }

    /**
     * Cíl existující rezervace. Volná místa se počítají včetně té rezervace samotné,
     * takže jde o stav „teď“, ne o to, kolik zbývalo při rezervaci.
     */
    override suspend fun targetFor(reference: Reference.Slot): ReservationTarget.Slot? {
        val service = services.get(reference.id) ?: return null
        val day = days(service, reference.start.date, reference.start.date).firstOrNull()
        val steps = day?.segments.orEmpty().filter { it.start >= reference.start && it.end <= reference.end }
        return service.slotTarget(reference.start, reference.end, freeSeats = steps.minOfOrNull { it.free } ?: 0)
    }
}

/**
 * Kroky [day], které úsek `[start, end)` pokrývá. null = úsek neleží celý v jednom
 * okně dne (začíná nebo končí mimo okno, nebo přechází přes mezeru mezi okny).
 */
fun stepsCovering(day: DayAvailability?, start: LocalDateTime, end: LocalDateTime): List<SlotSegment>? {
    val steps = day?.segments.orEmpty().filter { it.start >= start && it.end <= end }
    if (steps.isEmpty() || steps.first().start != start || steps.last().end != end) return null
    if (steps.zipWithNext().any { (a, b) -> a.end != b.start }) return null
    return steps
}

fun SlotService.slotTarget(start: LocalDateTime, end: LocalDateTime, freeSeats: Int) = ReservationTarget.Slot(
    serviceId = id,
    title = title,
    startDateTime = start,
    endDateTime = end,
    pricePerHour = pricePerHour,
    freeSeats = freeSeats,
    capacity = capacity,
    maxSeatsPerBooking = maxSeatsPerBooking,
    allowedPaymentTypes = allowedPaymentTypes,
    customFields = customFields,
    ownerEmails = ownerEmails,
)

private fun LocalDate.atStartOfDay() = LocalDateTime(this, LocalTime(0, 0))
