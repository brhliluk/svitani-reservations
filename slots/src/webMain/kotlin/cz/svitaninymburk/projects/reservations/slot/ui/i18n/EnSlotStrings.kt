package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotSelectionError
import cz.svitaninymburk.projects.reservations.ui.util.formatAmountNumber
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
    override val timeStep = "Time"
    override val dayFree = "free"
    override val dayFull = "full"
    override val noDays = "There are no free times in the coming days."
    override val noStepsThatDay = "This day can no longer be booked."
    override val decreaseSeats = "Fewer children"
    override val increaseSeats = "More children"
    override fun freeSeats(count: Int) = "$count free"
    override val stepFull = "full"
    override val timeHint = "Tap the start, then the last block you want."

    override fun selectionError(error: SlotSelectionError, maxDuration: Duration) = when (error) {
        SlotSelectionError.STEP_FULL -> "There is no room for this many children at that time."
        SlotSelectionError.CROSSES_UNAVAILABLE -> "The booking cannot span a full or closed time."
        SlotSelectionError.TOO_LONG -> "The longest possible booking is ${duration(maxDuration)}."
    }

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
