package cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase

import cz.svitaninymburk.projects.reservations.event.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

// --- Pure helpery (testovatelné bez RPC) ---

/** Předvyplnění formuláře: další lekce bývá za týden ve stejný čas jako ta poslední. */
data class AddLessonDefaults(val date: String, val startTime: String, val endTime: String)

fun addLessonDefaults(lastLesson: EventInstance?): AddLessonDefaults = AddLessonDefaults(
    date = lastLesson?.startDateTime?.date?.plus(7, DateTimeUnit.DAY)?.toString() ?: "",
    startTime = lastLesson?.startDateTime?.time?.toTimeInputValue() ?: "",
    endTime = lastLesson?.endDateTime?.time?.toTimeInputValue() ?: "",
)

fun LocalTime.toTimeInputValue(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

sealed interface AddLessonInput {
    data class Valid(val startDateTime: LocalDateTime, val endDateTime: LocalDateTime) : AddLessonInput
    data object Invalid : AddLessonInput
}

/** Lekce musí mít čitelné datum a čas, a konec až po začátku — jinak formulář neprojde. */
fun parseAddLessonInput(date: String, startTime: String, endTime: String): AddLessonInput {
    val parsedDate = try { LocalDate.parse(date) } catch (_: Exception) { return AddLessonInput.Invalid }
    val parsedStart = try { LocalTime.parse(startTime) } catch (_: Exception) { return AddLessonInput.Invalid }
    val parsedEnd = try { LocalTime.parse(endTime) } catch (_: Exception) { return AddLessonInput.Invalid }
    if (parsedEnd <= parsedStart) return AddLessonInput.Invalid
    return AddLessonInput.Valid(LocalDateTime(parsedDate, parsedStart), LocalDateTime(parsedDate, parsedEnd))
}
