package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotSelectionError
import kotlin.time.Duration

/**
 * Texty hlídání. Jazyk se řídí jádrem: modul nemá vlastní přepínač, jen podle
 * `strings.value.locale` sáhne po odpovídající sadě. Čte se ze stavu, takže se
 * při vykreslení přepne spolu s jádrem.
 */
val slotStrings: SlotStrings
    get() = when (strings.value.locale) {
        "en" -> EnSlotStrings
        else -> CsSlotStrings
    }

interface SlotStrings {
    /** Odkaz v hlavičce. */
    val navLink: String
    val chooseService: String
    /** „150 Kč za hodinu a dítě“ */
    fun pricePerHour(price: String): String
    val noServices: String
    val loadError: String
    val retry: String

    val dayStep: String
    val seatsStep: String
    val timeStep: String
    val dayFree: String
    val dayFull: String
    val noDays: String
    val noStepsThatDay: String
    val decreaseSeats: String
    val increaseSeats: String
    fun freeSeats(count: Int): String
    val stepFull: String
    val timeHint: String
    /** [maxDuration] null = služba nemá limit délky. */
    fun selectionError(error: SlotSelectionError, maxDuration: Duration?): String
    fun minDurationHint(minDuration: Duration): String

    /** „1 dítě“, „2 děti“, „5 dětí“. */
    fun children(count: Int): String
    /** „2,5 h“ */
    fun hours(hours: Double): String
    fun duration(duration: Duration): String
    val continueButton: String
}
