package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.slot.ui.usecase.EndLimit
import cz.svitaninymburk.projects.reservations.ui.util.formatAmountNumber
import cz.svitaninymburk.projects.reservations.util.hourMinute
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
    override val arrivalStep = "Kdy dítě přivedete"
    override val departureStep = "Kdy si ho vyzvednete"
    override val pickArrivalFirst = "Nejdřív vyberte, kdy dítě přivedete."
    override val exactTimeHint = "Přesný čas můžete doplnit do poznámky v následujícím kroku."
    override val clearSelection = "Zrušit výběr"
    override val dayFree = "volno"
    override val dayFull = "plno"
    override val noDays = "V nejbližších dnech není žádný volný termín."
    override val noStepsThatDay = "Tento den už nejde rezervovat."
    override val decreaseSeats = "Méně dětí"
    override val increaseSeats = "Více dětí"
    override val stepFull = "plno"

    override fun endLimit(limit: EndLimit) = when (limit) {
        is EndLimit.Full -> "V ${limit.at.time.hourMinute} už je pro zvolený počet dětí plno."
        is EndLimit.Closes -> "V ${limit.at.time.hourMinute} hlídání končí."
        is EndLimit.MaxDuration -> "Nejdelší možná rezervace je ${duration(limit.max)}."
    }

    override val legendFree = "volno"
    override fun legendFull(children: String) = "plno pro $children"
    override val legendSelected = "váš čas"
    override fun arrivalSummary(time: String) = "Příchod $time"
    override val pickDepartureHint = "Vyberte, kdy si dítě vyzvednete."

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
