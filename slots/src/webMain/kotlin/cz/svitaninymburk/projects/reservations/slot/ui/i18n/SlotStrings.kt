package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.EndLimit
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
    /** „Kdy dítě přivedete“ */
    val arrivalStep: String
    /** „Kdy si ho vyzvednete“ */
    val departureStep: String
    val pickArrivalFirst: String
    /** Kroky jsou zarovnané, přesný čas jde do poznámky v rezervačním formuláři. */
    val exactTimeHint: String
    val clearSelection: String
    val dayFree: String
    val dayFull: String
    val noDays: String
    val noStepsThatDay: String
    val decreaseSeats: String
    val increaseSeats: String
    val stepFull: String
    /** Věta pod nabídkou odchodů: proč nejde později. */
    fun endLimit(limit: EndLimit): String
    val legendFree: String
    /** [children] je už skloněné „2 děti“. */
    fun legendFull(children: String): String
    val legendSelected: String
    /** „Příchod 9:00“ v souhrnu, dokud chybí odchod. */
    fun arrivalSummary(time: String): String
    val pickDepartureHint: String
    fun minDurationHint(minDuration: Duration): String

    /** „1 dítě“, „2 děti“, „5 dětí“. */
    fun children(count: Int): String
    /** „2,5 h“ */
    fun hours(hours: Double): String
    fun duration(duration: Duration): String
    val continueButton: String
}
