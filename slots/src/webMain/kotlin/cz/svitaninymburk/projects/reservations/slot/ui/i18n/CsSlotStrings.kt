package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotSelectionError
import cz.svitaninymburk.projects.reservations.ui.util.formatAmountNumber
import kotlin.time.Duration

object CsSlotStrings : SlotStrings {
    override val navLink = "Hlídání"
    override val chooseService = "Vyberte službu"
    override fun pricePerHour(price: String) = "$price za hodinu a dítě"
    override val noServices = "Hlídání teď není v nabídce."
    override val loadError = "Nepodařilo se načíst volné termíny."
    override val retry = "Zkusit znovu"

    override val dayStep = "Den"
    override val seatsStep = "Počet dětí"
    override val timeStep = "Čas"
    override val dayFree = "volno"
    override val dayFull = "plno"
    override val noDays = "V nejbližších dnech není žádný volný termín."
    override val noStepsThatDay = "Tento den už nejde rezervovat."
    override val decreaseSeats = "Méně dětí"
    override val increaseSeats = "Více dětí"
    override fun freeSeats(count: Int) = "volno $count"
    override val stepFull = "plno"
    override val timeHint = "Klepněte na začátek a pak na poslední časový úsek, který chcete."

    override fun selectionError(error: SlotSelectionError, maxDuration: Duration?) = when (error) {
        SlotSelectionError.STEP_FULL -> "V tomto čase už pro zvolený počet dětí není místo."
        SlotSelectionError.CROSSES_UNAVAILABLE -> "Úsek nesmí přejít přes obsazený nebo zavřený čas."
        SlotSelectionError.TOO_LONG ->
            maxDuration?.let { "Nejdelší možná rezervace je ${duration(it)}." } ?: "Úsek je příliš dlouhý."
    }

    override fun minDurationHint(minDuration: Duration) = "Nejkratší možná rezervace je ${duration(minDuration)}."

    override fun children(count: Int) = when (count) {
        1 -> "1 dítě"
        in 2..4 -> "$count děti"
        else -> "$count dětí"
    }

    override fun hours(hours: Double) = "${formatAmountNumber(hours).replace('.', ',')} h"

    override fun duration(duration: Duration): String {
        val h = duration.inWholeHours
        val min = duration.inWholeMinutes % 60
        return when {
            h == 0L -> "$min min"
            min == 0L -> "$h h"
            else -> "$h h $min min"
        }
    }

    override val continueButton = "Pokračovat"
}
