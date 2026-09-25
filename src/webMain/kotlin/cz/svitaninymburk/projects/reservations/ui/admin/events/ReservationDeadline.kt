package cz.svitaninymburk.projects.reservations.ui.admin.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Uzávěrka rezervací ve formulářích akce a kurzu — stav pole [ReservationDeadlineSection]. */
class ReservationDeadlineState {
    var enabled by mutableStateOf(false)
    var typeIsHours by mutableStateOf(true)
    var hours by mutableIntStateOf(2)
    var daysBefore by mutableIntStateOf(1)
    var timeStr by mutableStateOf("18:00")
    var message by mutableStateOf("")

    /** Odstup uzávěrky od [start]; null = bez uzávěrky. */
    fun resolve(start: LocalDateTime): Duration? =
        resolveReservationDeadline(start, enabled, typeIsHours, hours, daysBefore, timeStr)

    /** Naplní pole z uložené akce. Server drží jen odstup, takže se vrací jako „N hodin předem“. */
    fun restore(deadline: Duration?, message: String?) {
        if (deadline != null) {
            enabled = true
            typeIsHours = true
            hours = deadline.inWholeHours.toInt()
        }
        this.message = message ?: ""
    }
}

fun resolveReservationDeadline(
    startDt: LocalDateTime,
    enabled: Boolean,
    typeIsHours: Boolean,
    hours: Int,
    daysBefore: Int,
    timeStr: String,
): Duration? {
    if (!enabled) return null
    if (typeIsHours) return hours.hours
    // Zóna prohlížeče, ne Europe/Prague: bundle nemá databázi časových pásem a TimeZone.of
    // tu spadne — výjimka by skončila v catch a uzávěrka by se tiše neuložila. Admin
    // formulář vyplňuje v Praze, takže odstup sedí i přes přechod na letní čas.
    return try {
        val tz = TimeZone.currentSystemDefault()
        val deadlineDate = startDt.date.minus(daysBefore, DateTimeUnit.DAY)
        val deadlineDateTime = LocalDateTime(deadlineDate, LocalTime.parse(timeStr))
        startDt.toInstant(tz) - deadlineDateTime.toInstant(tz)
    } catch (_: Exception) {
        null
    }
}
