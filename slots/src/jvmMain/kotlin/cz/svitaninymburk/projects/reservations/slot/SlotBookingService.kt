package cz.svitaninymburk.projects.reservations.slot

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.service.ReservationService
import cz.svitaninymburk.projects.reservations.slot.repository.SlotServiceRepository
import cz.svitaninymburk.projects.reservations.util.APP_TIMEZONE
import cz.svitaninymburk.projects.reservations.util.isAdminCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Rezervace úseků. Všechno, co přijde od klienta, se ověřuje znovu na serveru
 * a celé ověření i uložení běží pod zámkem služby: aplikace běží v jedné instanci,
 * takže [Mutex] stačí na to, aby dva souběžné požadavky nezabraly totéž poslední
 * místo. Platbu, peněženku, e-maily a audit pak dělá sdílený tok jádra.
 */
open class SlotBookingService(
    private val services: SlotServiceRepository,
    private val availability: SlotAvailability,
    private val reservationService: ReservationService,
    private val clock: Clock = Clock.System,
) : SlotServiceInterface {

    private val locks = ConcurrentHashMap<Uuid, Mutex>()

    /** Testovací seam, stejný jako v rezervační službě jádra. */
    internal open suspend fun isAdminCaller(): Boolean = isAdminCall()

    /** Nepublikovanou službu vidí a rezervuje jen admin, stejně jako nepublikované akce. */
    private suspend fun visibleService(id: Uuid): SlotService? =
        services.get(id)?.takeIf { it.isPublished || isAdminCaller() }

    override suspend fun listServices(): List<SlotService> =
        if (isAdminCaller()) services.getAll() else services.getAllPublished()

    override suspend fun getAvailability(serviceId: Uuid, from: LocalDate, to: LocalDate): List<DayAvailability> {
        val service = visibleService(serviceId) ?: return emptyList()
        val window = bookingWindow(service)
        val first = maxOf(from, window.opensAt.date)
        val last = minOf(to, window.closesAt.date)
        if (first > last) return emptyList()
        return availability.days(service, first, last).mapNotNull { day ->
            val bookable = day.segments.filter { it.start >= window.opensAt && it.end <= window.closesAt }
            if (bookable.isEmpty()) null else day.copy(segments = bookable)
        }
    }

    override suspend fun reserveSlot(
        request: CreateSlotReservationRequest,
        userId: Uuid?,
    ): Either<ReservationError.CreateReservation, Reservation> = either {
        locks.computeIfAbsent(request.serviceId) { Mutex() }.withLock {
            val service = ensureNotNull(visibleService(request.serviceId)) { ReservationError.ReservationNotFound }
            val start = request.start
            val end = request.end

            ensure(request.seatCount in 1..service.maxSeatsPerBooking) { ReservationError.InvalidSeatCount }
            ensure(
                end > start && start.date == end.date && start.isAlignedTo(service.step) && end.isAlignedTo(service.step)
            ) { ReservationError.SlotNotAligned }
            ensure(durationBetween(start.time, end.time) in service.minDuration..service.maxDuration) {
                ReservationError.SlotDurationOutOfRange
            }

            val day = availability.days(service, start.date, start.date).firstOrNull()
            val steps = ensureNotNull(stepsCovering(day, start, end)) { ReservationError.SlotOutsideOpeningHours }

            val window = bookingWindow(service)
            ensure(start > clock.now().toLocalDateTime(APP_TIMEZONE)) { ReservationError.EventAlreadyStarted }
            ensure(start >= window.opensAt) { ReservationError.ReservationDeadlinePassed }
            ensure(end <= window.closesAt) { ReservationError.SlotBeyondBookingHorizon }

            ensure(steps.all { it.free >= request.seatCount }) { ReservationError.SlotCapacityExceeded }

            reservationService.createSlotReservation(
                target = service.slotTarget(start, end, freeSeats = steps.minOf { it.free }),
                requestData = request,
                walletCode = request.walletCode,
                acknowledgedDuplicate = request.acknowledgedDuplicate,
                userId = userId,
            ).bind()
        }
    }

    /** Odkdy dokdy jde teď rezervovat: začátek až po uzávěrce, konec nejpozději na konci horizontu. */
    private fun bookingWindow(service: SlotService): BookingWindow {
        val now = clock.now()
        return BookingWindow(
            opensAt = (now + (service.reservationDeadline ?: Duration.ZERO)).toLocalDateTime(APP_TIMEZONE),
            closesAt = (now + service.bookingHorizon).toLocalDateTime(APP_TIMEZONE),
        )
    }

    private class BookingWindow(val opensAt: LocalDateTime, val closesAt: LocalDateTime)
}

/** Na krok se zarovnává od půlnoci, stejně jako v [expandAvailability]. */
private fun LocalDateTime.isAlignedTo(step: Duration): Boolean {
    val stepSeconds = step.inWholeSeconds
    return stepSeconds > 0 && time.nanosecond == 0 && time.toSecondOfDay() % stepSeconds == 0L
}

private fun durationBetween(start: LocalTime, end: LocalTime): Duration =
    (end.toSecondOfDay() - start.toSecondOfDay()).seconds
