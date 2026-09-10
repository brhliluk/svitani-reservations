package cz.svitaninymburk.projects.reservations.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Stav kalendáře: jen zobrazovaný měsíc. Není to [ScreenModel], protože
 * kalendář nic nenačítá ani nemění — akce dostává parametrem a klik posílá
 * nahoru. Vlastní scope ani toast by tu neměl co dělat.
 */
class CalendarModel(today: LocalDate) {

    /** První den zobrazovaného měsíce. */
    var month: LocalDate by mutableStateOf(LocalDate(today.year, today.month, 1)); private set

    fun previousMonth() { month = month.minus(1, DateTimeUnit.MONTH) }
    fun nextMonth() { month = month.plus(1, DateTimeUnit.MONTH) }
}

fun buildCalendarModel(): CalendarModel =
    CalendarModel(today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
