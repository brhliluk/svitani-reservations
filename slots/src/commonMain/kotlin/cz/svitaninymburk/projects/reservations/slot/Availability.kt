package cz.svitaninymburk.projects.reservations.slot

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.minutes

/**
 * Rozloží okna služby ve dnech [from]–[to] (včetně) na kroky délky [SlotService.step]
 * a u každého kroku odečte od kapacity místa rezervací, které se s ním překrývají.
 *
 * - Okna dne dávají pravidla platná pro ten den v týdnu a datum. Výjimka na den je
 *   všechna nahrazuje. Překrývající se nebo navazující okna se slijí do jednoho,
 *   rezervace pak může přes jejich styk přejít.
 * - Kroky jsou zarovnané na [SlotService.step] od půlnoci. Kus okna, do kterého se
 *   celý krok nevejde, se zahodí.
 * - Dny bez jediného kroku ve výsledku nejsou.
 *
 * Čistá funkce bez „teď“: uzávěrku a horizont rezervace řeší volající.
 */
fun expandAvailability(
    service: SlotService,
    rules: List<AvailabilityRule>,
    exceptions: List<AvailabilityException>,
    bookedIntervals: List<BookedInterval>,
    from: LocalDate,
    to: LocalDate,
): List<DayAvailability> {
    val stepMinutes = service.step.inWholeMinutes.toInt()
    require(stepMinutes > 0 && service.step == stepMinutes.minutes) {
        "Krok služby musí být kladný celý počet minut, je ${service.step}"
    }
    val days = mutableListOf<DayAvailability>()
    var date = from
    while (date <= to) {
        val segments = windowsOn(date, service, rules, exceptions).flatMap { window ->
            stepsOf(window, stepMinutes).map { (startMinute, endMinute) ->
                val start = date.atMinute(startMinute)
                val end = date.atMinute(endMinute)
                val booked = bookedIntervals.filter { it.start < end && it.end > start }.sumOf { it.seats }
                SlotSegment(start, end, free = (service.capacity - booked).coerceAtLeast(0), capacity = service.capacity)
            }
        }
        if (segments.isNotEmpty()) days += DayAvailability(date, segments)
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return days
}

/** Okno dne v minutách od půlnoci, `[start, end)`. */
internal data class MinuteWindow(val start: Int, val end: Int)

/**
 * Okna služby pro [date] po uplatnění výjimek, seřazená a slitá. Výjimka bez času
 * (ani zavřeno, ani náhradní okno) se bere jako zavřeno — radši nic nenabídnout
 * než otevřít den podle pravidel, která měla výjimka přepsat.
 */
internal fun windowsOn(
    date: LocalDate,
    service: SlotService,
    rules: List<AvailabilityRule>,
    exceptions: List<AvailabilityException>,
): List<MinuteWindow> {
    val dayExceptions = exceptions.filter { it.serviceId == service.id && it.date == date }
    val windows = if (dayExceptions.isNotEmpty()) {
        if (dayExceptions.any { it.closed }) return emptyList()
        dayExceptions.mapNotNull { e ->
            if (e.from == null || e.to == null) null else MinuteWindow(e.from.minuteOfDay(), e.to.minuteOfDay())
        }
    } else {
        rules
            .filter { r ->
                r.serviceId == service.id && r.dayOfWeek == date.dayOfWeek &&
                    r.validFrom <= date && (r.validTo == null || date <= r.validTo)
            }
            .map { MinuteWindow(it.from.minuteOfDay(), it.to.minuteOfDay()) }
    }
    return mergeWindows(windows.filter { it.end > it.start })
}

private fun mergeWindows(windows: List<MinuteWindow>): List<MinuteWindow> {
    val merged = mutableListOf<MinuteWindow>()
    for (window in windows.sortedBy { it.start }) {
        val last = merged.lastOrNull()
        if (last != null && window.start <= last.end) {
            merged[merged.lastIndex] = last.copy(end = maxOf(last.end, window.end))
        } else {
            merged += window
        }
    }
    return merged
}

private fun stepsOf(window: MinuteWindow, stepMinutes: Int): List<Pair<Int, Int>> {
    // Zaokrouhlit začátek nahoru na násobek kroku.
    var start = (window.start + stepMinutes - 1) / stepMinutes * stepMinutes
    val steps = mutableListOf<Pair<Int, Int>>()
    while (start + stepMinutes <= window.end) {
        steps += start to start + stepMinutes
        start += stepMinutes
    }
    return steps
}

private fun LocalTime.minuteOfDay(): Int = toSecondOfDay() / 60

private fun LocalDate.atMinute(minuteOfDay: Int): LocalDateTime =
    LocalDateTime(this, LocalTime.fromSecondOfDay(minuteOfDay * 60))
