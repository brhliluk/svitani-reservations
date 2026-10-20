package cz.svitaninymburk.projects.reservations.slot.ui.i18n

import cz.svitaninymburk.projects.reservations.i18n.strings
import cz.svitaninymburk.projects.reservations.slot.ScheduleProblem
import cz.svitaninymburk.projects.reservations.slot.SlotAdminError
import cz.svitaninymburk.projects.reservations.slot.SlotServiceProblem

/** Texty administrace hlídání; jazyk podle jádra, stejně jako [slotStrings]. */
val slotAdminStrings: SlotAdminStrings
    get() = when (strings.value.locale) {
        "en" -> EnSlotAdminStrings
        else -> CsSlotAdminStrings
    }

interface SlotAdminStrings {
    // Seznam
    val navLabel: String
    val listTitle: String
    val listSubtitle: String
    val newService: String
    val noServicesYet: String
    val published: String
    val hidden: String
    fun serviceSummary(price: String, capacity: Int, stepMinutes: Long): String
    val loadError: String
    val retry: String

    // Detail
    val backToList: String
    val tabDay: String
    val tabSchedule: String
    val tabSettings: String
    val publish: String
    val unpublish: String
    val deleteService: String
    val deleteTitle: String
    fun deleteText(title: String, reservationCount: Int): String
    val refundCredit: String
    val confirmDelete: String
    val saved: String
    val publishedToast: String
    val hiddenToast: String
    val deletedToast: String

    // Den
    val previousDay: String
    val nextDay: String
    val today: String
    val print: String
    val closedThatDay: String
    val reservationsHeading: String
    val noReservationsThatDay: String
    fun occupancy(occupied: Int, capacity: Int): String
    fun dayTotal(reservations: Int, children: String): String

    // Rozvrh
    val periodsHeading: String
    val periodsHint: String
    val validFrom: String
    val validTo: String
    val addPeriod: String
    val removePeriod: String
    val addWindow: String
    val removeWindow: String
    val closedDay: String
    val exceptionsHeading: String
    val exceptionsHint: String
    val exceptionDate: String
    val exceptionClosed: String
    val addException: String
    val removeException: String
    val noExceptions: String
    val noPeriods: String
    val saveSchedule: String
    val affectedTitle: String
    fun affectedText(count: Int): String
    val saveAnyway: String
    fun scheduleProblem(problem: ScheduleProblem): String

    // Nastavení
    val titleLabel: String
    val descriptionLabel: String
    val stepLabel: String
    fun stepOption(minutes: Long): String
    val minDurationLabel: String
    val maxDurationLabel: String
    val minDurationFieldHint: String
    val maxDurationFieldHint: String
    val minutesSuffix: String
    val priceLabel: String
    val capacityLabel: String
    val maxSeatsLabel: String
    val deadlineLabel: String
    val deadlineHint: String
    val horizonLabel: String
    val formFieldsHeading: String
    val formFieldsHint: String
    /** Výchozí pole nové služby. */
    val defaultChildrenField: String
    val defaultNoteField: String
    val save: String
    /** Tlačítko formuláře nové služby — po založení pokračuje na záložku Rozvrh. */
    val continueToSchedule: String
    fun serviceProblem(problem: SlotServiceProblem): String

    fun error(error: SlotAdminError): String = when (error) {
        SlotAdminError.ServiceNotFound -> serviceNotFound
        is SlotAdminError.InvalidService -> error.problems.joinToString(" ") { serviceProblem(it) }
        is SlotAdminError.InvalidSchedule -> error.problems.joinToString(" ") { scheduleProblem(it) }
    }
    val serviceNotFound: String
}
