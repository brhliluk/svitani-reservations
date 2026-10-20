package cz.svitaninymburk.projects.reservations.slot

import cz.svitaninymburk.projects.reservations.event.PriceModifier
import cz.svitaninymburk.projects.reservations.event.TimeRangeFieldDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

// Kontroly nastavení a rozvrhu. Běží na serveru (rozhoduje) i ve formuláři (ukáže chyby
// hned u polí), proto jsou tady v common části.

/** Povolené kroky zarovnání. */
val SLOT_STEPS: List<Duration> = listOf(15.minutes, 30.minutes, 60.minutes)

@Serializable
enum class SlotServiceProblem {
    TITLE_BLANK,
    STEP_INVALID,
    /** Nejkratší úsek nesmí být záporný a musí být celý počet kroků (0 = bez minima). */
    MIN_DURATION_INVALID,
    /** Nejdelší úsek (je-li zadaný) musí být aspoň nejkratší, aspoň jeden krok a celý počet kroků. */
    MAX_DURATION_INVALID,
    PRICE_NEGATIVE,
    CAPACITY_INVALID,
    /** Aspoň 1 a nejvýš kapacita. */
    MAX_SEATS_INVALID,
    HORIZON_INVALID,
    DEADLINE_NEGATIVE,
    NO_PAYMENT_TYPE,
    OWNER_EMAIL_INVALID,
    /** Hlídání násobí cenu hodinami samo; časové pole s násobením by ji násobilo podruhé. */
    TIME_MULTIPLIER_FIELD,
}

fun validateSlotService(service: SlotService): List<SlotServiceProblem> = buildList {
    if (service.title.isBlank()) add(SlotServiceProblem.TITLE_BLANK)
    val stepValid = service.step in SLOT_STEPS
    if (!stepValid) add(SlotServiceProblem.STEP_INVALID)
    fun Duration.isWholeSteps() = !stepValid || inWholeMinutes % service.step.inWholeMinutes == 0L
    if (service.minDuration < Duration.ZERO || !service.minDuration.isWholeSteps()) add(SlotServiceProblem.MIN_DURATION_INVALID)
    service.maxDuration?.let { max ->
        if (max < service.minDuration || max < service.step || !max.isWholeSteps()) add(SlotServiceProblem.MAX_DURATION_INVALID)
    }
    if (service.pricePerHour < 0.0) add(SlotServiceProblem.PRICE_NEGATIVE)
    if (service.capacity < 1) add(SlotServiceProblem.CAPACITY_INVALID)
    if (service.maxSeatsPerBooking < 1 || service.maxSeatsPerBooking > service.capacity.coerceAtLeast(1)) {
        add(SlotServiceProblem.MAX_SEATS_INVALID)
    }
    if (service.bookingHorizon < 1.days) add(SlotServiceProblem.HORIZON_INVALID)
    if ((service.reservationDeadline ?: Duration.ZERO) < Duration.ZERO) add(SlotServiceProblem.DEADLINE_NEGATIVE)
    if (service.allowedPaymentTypes.none { it != PaymentType.FREE } && service.pricePerHour > 0.0) {
        add(SlotServiceProblem.NO_PAYMENT_TYPE)
    }
    if (service.ownerEmails.any { !it.contains('@') }) add(SlotServiceProblem.OWNER_EMAIL_INVALID)
    if (service.customFields.any { it is TimeRangeFieldDefinition && it.priceModifier is PriceModifier.TimeMultiplier }) {
        add(SlotServiceProblem.TIME_MULTIPLIER_FIELD)
    }
}

@Serializable
enum class ScheduleProblem {
    /** Okno, které nekončí po svém začátku. */
    WINDOW_EMPTY,
    /** Období končí dřív, než začne. */
    PERIOD_DATES,
    /** Dvě období platí ve stejné dny — nebylo by jasné, který rozvrh platí. */
    PERIODS_OVERLAP,
    /** Výjimka, která není „zavřeno“, potřebuje okno. */
    EXCEPTION_WINDOW_MISSING,
    /** Nevyplněný čas nebo datum ve formuláři (server ho nikdy nevrací). */
    INCOMPLETE,
}

/** Časové okno jednoho dne. */
@Serializable
data class TimeWindow(val from: LocalTime, val to: LocalTime)

/**
 * Období rozvrhu: od [validFrom] do [validTo] (včetně, null = bez konce) platí týden
 * [windows]. V databázi je to sada pravidel se stejnou platností.
 */
@Serializable
data class SchedulePeriod(
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val windows: Map<DayOfWeek, List<TimeWindow>>,
)

/** Pravidla seskupená do období podle platnosti, seřazená podle začátku. */
fun rulesToPeriods(rules: List<AvailabilityRule>): List<SchedulePeriod> =
    rules.groupBy { it.validFrom to it.validTo }
        .map { (validity, periodRules) ->
            SchedulePeriod(
                validFrom = validity.first,
                validTo = validity.second,
                windows = periodRules
                    .groupBy { it.dayOfWeek }
                    .mapValues { (_, dayRules) -> dayRules.sortedBy { it.from }.map { TimeWindow(it.from, it.to) } },
            )
        }
        .sortedBy { it.validFrom }

/** Zpět na pravidla k uložení. Nová id; rozvrh se ukládá vždy celý. */
fun periodsToRules(serviceId: Uuid, periods: List<SchedulePeriod>): List<AvailabilityRule> =
    periods.flatMap { period ->
        DayOfWeek.entries.flatMap { day ->
            period.windows[day].orEmpty().map { window ->
                AvailabilityRule(
                    id = Uuid.random(),
                    serviceId = serviceId,
                    dayOfWeek = day,
                    from = window.from,
                    to = window.to,
                    validFrom = period.validFrom,
                    validTo = period.validTo,
                )
            }
        }
    }

fun validateSchedule(rules: List<AvailabilityRule>, exceptions: List<AvailabilityException>): List<ScheduleProblem> = buildList {
    if (rules.any { it.to <= it.from }) add(ScheduleProblem.WINDOW_EMPTY)
    if (rules.any { it.validTo != null && it.validTo < it.validFrom }) add(ScheduleProblem.PERIOD_DATES)
    val periods = rulesToPeriods(rules)
    val overlapping = periods.indices.any { i ->
        (i + 1 until periods.size).any { j -> periods[i].overlaps(periods[j]) }
    }
    if (overlapping) add(ScheduleProblem.PERIODS_OVERLAP)
    exceptions.filter { !it.closed }.forEach { exception ->
        val from = exception.from
        val to = exception.to
        when {
            from == null || to == null -> add(ScheduleProblem.EXCEPTION_WINDOW_MISSING)
            to <= from -> add(ScheduleProblem.WINDOW_EMPTY)
        }
    }
}.distinct()

private fun SchedulePeriod.overlaps(other: SchedulePeriod): Boolean {
    val thisEndsBefore = validTo != null && validTo < other.validFrom
    val otherEndsBefore = other.validTo != null && other.validTo < validFrom
    return !thisEndsBefore && !otherEndsBefore
}
