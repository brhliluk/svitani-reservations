package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.slot.ui.usecase.EndLimit
import cz.svitaninymburk.projects.reservations.ui.util.formatAmountNumber
import cz.svitaninymburk.projects.reservations.util.hourMinute
import kotlin.time.Duration

object EnSlotStrings : SlotStrings {
    override val navLink = "Childcare"
    override val chooseService = "Choose a service"
    override fun pricePerHour(price: String) = "$price per hour per child"
    override val noServices = "Childcare is not on offer right now."
    override val loadError = "Could not load available times."
    override val retry = "Try again"

    override val dayStep = "Day"
    override val seatsStep = "Number of children"
    override val arrivalStep = "Drop-off time"
    override val departureStep = "Pick-up time"
    override val pickArrivalFirst = "Choose the drop-off time first."
    override val exactTimeHint = "You can add the exact time to the note in the next step."
    override val clearSelection = "Clear"
    override val dayFree = "free"
    override val dayFull = "full"
    override val noDays = "There are no free times in the coming days."
    override val noStepsThatDay = "This day can no longer be booked."
    override val decreaseSeats = "Fewer children"
    override val increaseSeats = "More children"
    override val stepFull = "full"

    override fun endLimit(limit: EndLimit) = when (limit) {
        is EndLimit.Full -> "From ${limit.at.time.hourMinute} there is no room for this many children."
        is EndLimit.Closes -> "Childcare ends at ${limit.at.time.hourMinute}."
        is EndLimit.MaxDuration -> "The longest possible booking is ${duration(limit.max)}."
    }

    override val legendFree = "free"
    override fun legendFull(children: String) = "full for $children"
    override val legendSelected = "your time"
    override fun arrivalSummary(time: String) = "Drop-off $time"
    override val pickDepartureHint = "Choose the pick-up time."

    override fun minDurationHint(minDuration: Duration) = "The shortest possible booking is ${duration(minDuration)}."

    override fun children(count: Int) = if (count == 1) "1 child" else "$count children"

    override fun hours(hours: Double) = "${formatAmountNumber(hours)} h"

    override fun duration(duration: Duration): String {
        val h = duration.inWholeHours
        val min = duration.inWholeMinutes % 60
        return when {
            h == 0L -> "$min min"
            min == 0L -> "$h h"
            else -> "$h h $min min"
        }
    }

    override val continueButton = "Continue"
}
