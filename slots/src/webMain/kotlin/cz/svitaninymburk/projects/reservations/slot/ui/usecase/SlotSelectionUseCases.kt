package cz.svitaninymburk.projects.reservations.slot.ui.usecase

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

/**
 * Rozpracovaný výběr: příchod a případně odchod. [last] je index posledního kroku úseku
 * (odchod = jeho konec), `null` = odchod ještě není vybraný.
 */
data class SlotPick(val start: Int, val last: Int? = null) {
    val selection: SlotSelection? get() = last?.let { SlotSelection(start, it) }
}

/** Proč nabídka odchodů končí tam, kde končí. */
sealed interface EndLimit {
    /** Další krok už nemá pro zvolený počet dětí místo. */
    data class Full(val at: LocalDateTime) : EndLimit

    /** Okno hlídání v tu chvíli končí. */
    data class Closes(val at: LocalDateTime) : EndLimit

    /** Delší rezervaci služba nedovolí. */
    data class MaxDuration(val max: Duration) : EndLimit
}

fun isStepSelectable(segment: SlotSegment, seats: Int): Boolean = segment.free >= seats

/** Kroky na sebe navazují. Mezera mezi nimi je hranice mezi okny, přes kterou úsek nejde. */
private fun follows(previous: SlotSegment, next: SlotSegment): Boolean = previous.end == next.start

private fun durationBetween(start: LocalDateTime, end: LocalDateTime): Duration =
    (end.time.toSecondOfDay() - start.time.toSecondOfDay()).seconds

/** Souvislá okna dne jako rozsahy indexů kroků. */
fun windows(segments: List<SlotSegment>): List<IntRange> {
    val result = mutableListOf<IntRange>()
    var windowStart = 0
    for (index in 1..segments.size) {
        if (index == segments.size || !follows(segments[index - 1], segments[index])) {
            if (index > windowStart) result += windowStart until index
            windowStart = index
        }
    }
    return result
}

/** Nejdelší souvislý běh volných kroků od [start] v mezích maximální délky služby. */
private fun lastReachable(start: Int, segments: List<SlotSegment>, seats: Int, service: SlotService): Int {
    val from = segments[start].start
    var last = start
    while (true) {
        val next = segments.getOrNull(last + 1) ?: break
        if (!follows(segments[last], next) || !isStepSelectable(next, seats)) break
        val maxDuration = service.maxDuration
        if (maxDuration != null && durationBetween(from, next.end) > maxDuration) break
        last++
    }
    return last
}

/**
 * Poslední kroky, se kterými jde z příchodu [start] rezervovat: úsek je souvislý, má místo
 * pro všechny děti a délku mezi minimem a maximem služby. Prázdné = odsud rezervovat nejde.
 */
fun possibleLasts(start: Int, segments: List<SlotSegment>, seats: Int, service: SlotService): IntRange {
    val first = segments.getOrNull(start) ?: return IntRange.EMPTY
    if (!isStepSelectable(first, seats)) return IntRange.EMPTY
    val last = lastReachable(start, segments, seats, service)
    val shortest = (start..last).firstOrNull { durationBetween(first.start, segments[it].end) >= service.minDuration }
        ?: return IntRange.EMPTY
    return shortest..last
}

fun isStartAvailable(start: Int, segments: List<SlotSegment>, seats: Int, service: SlotService): Boolean =
    !possibleLasts(start, segments, seats, service).isEmpty()

/** Důvod, proč odchod z příchodu [start] nejde později. `null`, když z něj rezervovat nejde vůbec. */
fun endLimit(start: Int, segments: List<SlotSegment>, seats: Int, service: SlotService): EndLimit? {
    val lasts = possibleLasts(start, segments, seats, service)
    if (lasts.isEmpty()) return null
    val last = segments[lasts.last]
    val next = segments.getOrNull(lasts.last + 1)?.takeIf { follows(last, it) }
    return when {
        next == null -> EndLimit.Closes(last.end)
        !isStepSelectable(next, seats) -> EndLimit.Full(next.start)
        else -> service.maxDuration?.let { EndLimit.MaxDuration(it) } ?: EndLimit.Closes(last.end)
    }
}

/**
 * Klepnutí na příchod [tapped]. Na vybraný příchod výběr zruší. Jiný příchod nechá odchod,
 * pokud z nového příchodu pořád jde, jinak ho vynuluje. Příchod, ze kterého rezervovat nejde,
 * výběr nezmění (stránka ho stejně nenabízí).
 */
fun pickStart(current: SlotPick?, tapped: Int, segments: List<SlotSegment>, seats: Int, service: SlotService): SlotPick? {
    if (current?.start == tapped) return null
    val lasts = possibleLasts(tapped, segments, seats, service)
    if (lasts.isEmpty()) return current
    return SlotPick(tapped, current?.last?.takeIf { it in lasts })
}

/** Klepnutí na odchod (index posledního kroku). Na vybraný odchod ho zruší, nenabízený výběr nezmění. */
fun pickLast(current: SlotPick, tapped: Int, segments: List<SlotSegment>, seats: Int, service: SlotService): SlotPick =
    when {
        current.last == tapped -> current.copy(last = null)
        tapped in possibleLasts(current.start, segments, seats, service) -> current.copy(last = tapped)
        else -> current
    }

/** Výběr po změně počtu dětí: co pořád platí, zůstane. Neplatný odchod zmizí, neplatný příchod celý výběr. */
fun keepPick(current: SlotPick?, segments: List<SlotSegment>, seats: Int, service: SlotService): SlotPick? {
    current ?: return null
    val lasts = possibleLasts(current.start, segments, seats, service)
    if (lasts.isEmpty()) return null
    return current.copy(last = current.last?.takeIf { it in lasts })
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

/** Úsek splňuje minimální délku. Výběr přes [pickLast] ji splní vždy, tohle je pojistka tlačítka. */
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
