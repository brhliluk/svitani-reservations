package cz.svitaninymburk.projects.reservations.slot.ui.usecase

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.event.calculateTotalPrice
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.slot.DayAvailability
import cz.svitaninymburk.projects.reservations.slot.SlotSegment
import cz.svitaninymburk.projects.reservations.slot.SlotService
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Výběr úseku na stránce hlídání. Všechno tady je jen nápověda pro rodiče —
// server celý úsek ověří znovu (SlotBookingService.reserveSlot).

/** Vybraný úsek jako rozsah indexů kroků dne, oba včetně. */
data class SlotSelection(val first: Int, val last: Int)

enum class SlotSelectionError {
    /** Klepnutý krok nemá pro zvolený počet dětí místo. */
    STEP_FULL,

    /** Úsek by přešel přes plný krok nebo přes hranici mezi okny (zavřeno). */
    CROSSES_UNAVAILABLE,

    /** Úsek by byl delší, než služba dovolí (jen u služby s limitem délky). */
    TOO_LONG,
}

fun isStepSelectable(segment: SlotSegment, seats: Int): Boolean = segment.free >= seats

/** Kroky na sebe navazují. Mezera mezi nimi je hranice mezi okny, přes kterou úsek nejde. */
private fun follows(previous: SlotSegment, next: SlotSegment): Boolean = previous.end == next.start

private fun durationBetween(start: LocalDateTime, end: LocalDateTime): Duration =
    (end.time.toSecondOfDay() - start.time.toSecondOfDay()).seconds

/**
 * Nový výběr po klepnutí na krok [tappedIndex]. První klepnutí nastaví začátek, klepnutí
 * na pozdější krok posune konec (i zpět, výběr se tak dá zkrátit). Klepnutí před začátek
 * začne nový výběr a klepnutí na jediný vybraný krok výběr zruší (`null`).
 *
 * Při chybě zůstává výběr, jaký byl, a stránka ukáže hlášku.
 */
fun extendSelection(
    current: SlotSelection?,
    tappedIndex: Int,
    segments: List<SlotSegment>,
    seats: Int,
    service: SlotService,
): Either<SlotSelectionError, SlotSelection?> {
    val tapped = segments.getOrNull(tappedIndex) ?: return Either.Right(current)
    if (current == null || tappedIndex < current.first) {
        return if (isStepSelectable(tapped, seats)) {
            Either.Right(SlotSelection(tappedIndex, tappedIndex))
        } else {
            Either.Left(SlotSelectionError.STEP_FULL)
        }
    }
    if (tappedIndex == current.first && current.last == current.first) return Either.Right(null)

    val range = current.first..tappedIndex
    val passable = range.all { isStepSelectable(segments[it], seats) } &&
        (current.first until tappedIndex).all { follows(segments[it], segments[it + 1]) }
    if (!passable) return Either.Left(SlotSelectionError.CROSSES_UNAVAILABLE)

    val candidate = SlotSelection(current.first, tappedIndex)
    val maxDuration = service.maxDuration
    if (maxDuration != null && selectionDuration(candidate, segments) > maxDuration) return Either.Left(SlotSelectionError.TOO_LONG)
    return Either.Right(candidate)
}

fun selectionStart(selection: SlotSelection, segments: List<SlotSegment>): LocalDateTime = segments[selection.first].start
fun selectionEnd(selection: SlotSelection, segments: List<SlotSegment>): LocalDateTime = segments[selection.last].end

fun selectionDuration(selection: SlotSelection, segments: List<SlotSegment>): Duration =
    durationBetween(selectionStart(selection, segments), selectionEnd(selection, segments))

fun selectionHours(selection: SlotSelection, segments: List<SlotSegment>): Double =
    selectionDuration(selection, segments).inWholeMinutes / 60.0

/**
 * Cena úseku bez vlastních polí (ta se vyplní až ve formuláři). Přesně, bez zaokrouhlení,
 * stejným výpočtem jako server.
 */
fun selectionPrice(service: SlotService, seats: Int, hours: Double): Double =
    calculateTotalPrice(
        basePrice = service.pricePerHour,
        seatCount = seats,
        customFields = emptyList(),
        customValues = emptyMap(),
        timeMultiplier = hours,
    )

/** Úsek splňuje minimální délku (maximum hlídá už [extendSelection]). Minimum 0 splní každý výběr. */
fun isSelectionLongEnough(selection: SlotSelection, segments: List<SlotSegment>, service: SlotService): Boolean =
    selectionDuration(selection, segments) >= service.minDuration

/**
 * Jde ten den pro [seats] dětí zarezervovat aspoň nejkratší úsek? Hledá souvislý
 * běh volných kroků dlouhý aspoň [minDuration]. Při minimu 0 stačí jeden volný krok.
 */
fun dayHasAvailability(day: DayAvailability, seats: Int, minDuration: Duration = Duration.ZERO): Boolean {
    var runStart: LocalDateTime? = null
    var previous: SlotSegment? = null
    for (segment in day.segments) {
        if (!isStepSelectable(segment, seats)) {
            runStart = null
        } else {
            if (runStart == null || previous == null || !follows(previous, segment)) runStart = segment.start
            if (durationBetween(runStart, segment.end) >= minDuration) return true
        }
        previous = segment
    }
    return false
}

/** Den, který se otevře jako první: nejbližší s volnem, jinak prostě nejbližší. */
fun initialDay(days: List<DayAvailability>, seats: Int, minDuration: Duration): LocalDate? =
    (days.firstOrNull { dayHasAvailability(it, seats, minDuration) } ?: days.firstOrNull())?.date

/** Nejvíc dětí na jednu rezervaci: limit služby, ale nikdy víc než její kapacita. */
fun maxSeatsFor(service: SlotService): Int = minOf(service.maxSeatsPerBooking, service.capacity).coerceAtLeast(1)

/**
 * Dny, na které se ptát serveru: od dneška do konce horizontu. Den navíc kvůli tomu, že
 * „dnes“ tu je podle prohlížeče — server rozsah ořízne podle sebe.
 */
fun availabilityRange(today: LocalDate, service: SlotService): ClosedRange<LocalDate> =
    today..today.plus(service.bookingHorizon.inWholeDays.toInt() + 1, DateTimeUnit.DAY)

/** Cíl rezervačního formuláře pro vybraný úsek. Volná místa = nejmenší počet přes celý úsek. */
fun selectionTarget(service: SlotService, selection: SlotSelection, segments: List<SlotSegment>): ReservationTarget.Slot =
    ReservationTarget.Slot(
        serviceId = service.id,
        title = service.title,
        startDateTime = selectionStart(selection, segments),
        endDateTime = selectionEnd(selection, segments),
        pricePerHour = service.pricePerHour,
        freeSeats = (selection.first..selection.last).minOf { segments[it].free },
        capacity = service.capacity,
        maxSeatsPerBooking = service.maxSeatsPerBooking,
        allowedPaymentTypes = service.allowedPaymentTypes,
        customFields = service.customFields,
        ownerEmails = service.ownerEmails,
    )
