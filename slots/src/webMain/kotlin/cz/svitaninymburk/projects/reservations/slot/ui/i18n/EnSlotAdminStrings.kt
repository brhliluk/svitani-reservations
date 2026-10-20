package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.slot.ScheduleProblem
import cz.svitaninymburk.projects.reservations.slot.SlotServiceProblem

object EnSlotAdminStrings : SlotAdminStrings {
    override val navLabel = "Childcare"
    override val listTitle = "Childcare"
    override val listSubtitle = "Services where parents book any block within opening hours."
    override val newService = "New service"
    override val noServicesYet = "There are no services yet."
    override val published = "Published"
    override val hidden = "Hidden"
    override fun serviceSummary(price: String, capacity: Int, stepMinutes: Long) =
        "$price per hour per child · capacity $capacity · step $stepMinutes min"
    override val loadError = "Could not load data."
    override val retry = "Try again"

    override val backToList = "Back to childcare"
    override val tabDay = "Day"
    override val tabSchedule = "Schedule"
    override val tabSettings = "Settings"
    override val publish = "Publish"
    override val unpublish = "Hide"
    override val deleteService = "Delete service"
    override val deleteTitle = "Delete service?"
    override fun deleteText(title: String, reservationCount: Int) = buildString {
        append("The service “$title” will be deleted together with its schedule.")
        if (reservationCount > 0) {
            append(" Active reservations (upcoming: $reservationCount) will be cancelled and parents notified by e-mail.")
        }
    }
    override val refundCredit = "Refund paid amounts as wallet credit"
    override val confirmDelete = "Delete"
    override val saved = "Saved."
    override val publishedToast = "The service is published."
    override val hiddenToast = "The service is hidden, only admins can see it."
    override val deletedToast = "The service has been deleted."

    override val previousDay = "Previous day"
    override val nextDay = "Next day"
    override val today = "Today"
    override val print = "Print"
    override val closedThatDay = "Closed on this day."
    override val reservationsHeading = "Reservations"
    override val noReservationsThatDay = "No reservations on this day."
    override fun occupancy(occupied: Int, capacity: Int) = "$occupied/$capacity"
    override fun dayTotal(reservations: Int, children: String) = "Reservations: $reservations · $children"

    override val periodsHeading = "Opening hours"
    override val periodsHint = "Each period has its own week. Periods must not overlap."
    override val validFrom = "Valid from"
    override val validTo = "Valid to"
    override val addPeriod = "Add period"
    override val removePeriod = "Remove period"
    override val addWindow = "Add time"
    override val removeWindow = "Remove time"
    override val closedDay = "closed"
    override val exceptionsHeading = "Exceptions"
    override val exceptionsHint = "An exception replaces the schedule of that day: either closed or different hours."
    override val exceptionDate = "Date"
    override val exceptionClosed = "Closed all day"
    override val addException = "Add exception"
    override val removeException = "Remove exception"
    override val noExceptions = "No exceptions."
    override val noPeriods = "No opening hours have been set yet."
    override val saveSchedule = "Save schedule"
    override val affectedTitle = "This change affects reservations"
    override fun affectedText(count: Int) =
        "After saving, $count active reservations will be outside opening hours. Nothing is cancelled " +
            "automatically — cancel them in the reservation list if needed."
    override val saveAnyway = "Save anyway"
    override fun scheduleProblem(problem: ScheduleProblem) = when (problem) {
        ScheduleProblem.WINDOW_EMPTY -> "A time must end after it starts."
        ScheduleProblem.PERIOD_DATES -> "A period must end after it starts."
        ScheduleProblem.PERIODS_OVERLAP -> "Periods overlap."
        ScheduleProblem.EXCEPTION_WINDOW_MISSING -> "An exception that is not closed needs a time."
        ScheduleProblem.INCOMPLETE -> "Fill in all dates and times."
    }

    override val titleLabel = "Title"
    override val descriptionLabel = "Description"
    override val stepLabel = "Step (start and end alignment)"
    override fun stepOption(minutes: Long) = "$minutes min"
    override val minDurationLabel = "Shortest booking"
    override val maxDurationLabel = "Longest booking"
    override val minDurationFieldHint = "0 = no minimum, one step is enough."
    override val maxDurationFieldHint = "0 = no limit."
    override val minutesSuffix = "min"
    override val priceLabel = "Price per hour per child"
    override val capacityLabel = "Capacity (children at once)"
    override val maxSeatsLabel = "Most children per booking"
    override val deadlineLabel = "Deadline (hours before start)"
    override val deadlineHint = "0 = bookable until the start."
    override val horizonLabel = "Book ahead (days)"
    override val formFieldsHeading = "Booking form fields"
    override val formFieldsHint = "E.g. children's names and ages, or a note about drop-off. Childcare multiplies the price by hours itself."
    override val defaultChildrenField = "Children's names and ages"
    override val defaultNoteField = "Note (e.g. drop-off and pick-up times)"
    override val save = "Save"
    override val continueToSchedule = "Continue to opening hours"
    override fun serviceProblem(problem: SlotServiceProblem) = when (problem) {
        SlotServiceProblem.TITLE_BLANK -> "Fill in the title."
        SlotServiceProblem.STEP_INVALID -> "The step must be 15, 30 or 60 minutes."
        SlotServiceProblem.MIN_DURATION_INVALID -> "The shortest booking must be a whole number of steps (0 = no minimum)."
        SlotServiceProblem.MAX_DURATION_INVALID -> "The longest booking must be at least the shortest, at least one step and a whole number of steps (0 = no limit)."
        SlotServiceProblem.PRICE_NEGATIVE -> "The price must not be negative."
        SlotServiceProblem.CAPACITY_INVALID -> "Capacity must be at least 1."
        SlotServiceProblem.MAX_SEATS_INVALID -> "Children per booking must be between 1 and the capacity."
        SlotServiceProblem.HORIZON_INVALID -> "Booking must be possible at least one day ahead."
        SlotServiceProblem.DEADLINE_NEGATIVE -> "The deadline must not be negative."
        SlotServiceProblem.NO_PAYMENT_TYPE -> "Choose at least one payment method."
        SlotServiceProblem.OWNER_EMAIL_INVALID -> "Check the lecturer e-mails."
        SlotServiceProblem.TIME_MULTIPLIER_FIELD -> "A time field must not multiply the price, childcare does that itself."
    }
    override val serviceNotFound = "Service not found."
}
