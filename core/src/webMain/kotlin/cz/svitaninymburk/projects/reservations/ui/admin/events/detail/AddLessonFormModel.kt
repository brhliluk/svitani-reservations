package cz.svitaninymburk.projects.reservations.ui.admin.events.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.svitaninymburk.projects.reservations.event.EventInstance
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.AddLessonInput
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.addLessonDefaults
import cz.svitaninymburk.projects.reservations.ui.admin.events.detail.usecase.parseAddLessonInput

/**
 * Formulář "přidat lekci". Odesílání ani chybu si nedrží — obojí řeší
 * `AdminEventDetailModel`, dialog jen posbírá vstup a nechá si ho ověřit.
 */
class AddLessonFormModel(lastLesson: EventInstance?) {

    private val defaults = addLessonDefaults(lastLesson)

    var date by mutableStateOf(defaults.date)
    var startTime by mutableStateOf(defaults.startTime)
    var endTime by mutableStateOf(defaults.endTime)
    var isDropIn by mutableStateOf(false)

    fun parsed(): AddLessonInput = parseAddLessonInput(date, startTime, endTime)
}
