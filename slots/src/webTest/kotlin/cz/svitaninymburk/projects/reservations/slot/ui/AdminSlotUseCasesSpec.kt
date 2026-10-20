package cz.svitaninymburk.projects.reservations.slot.ui

import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.slot.AvailabilityException
import cz.svitaninymburk.projects.reservations.slot.AvailabilityRule
import cz.svitaninymburk.projects.reservations.slot.ScheduleProblem
import cz.svitaninymburk.projects.reservations.slot.SlotReservationRow
import cz.svitaninymburk.projects.reservations.slot.SlotServiceProblem
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.ExceptionDraft
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.PeriodDraft
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.ScheduleDraft
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.SlotServiceForm
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.WindowDraft
import cz.svitaninymburk.projects.reservations.slot.ui.admin.SlotAdminTab
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.adminReservationLink
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.adminSlotServiceLink
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.dayChildrenTotal
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.formOf
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.formProblems
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.newServiceForm
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.reservationTimeLabel
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.scheduleDraftOf
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.shiftDay
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.slotAdminTabOf
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.toSchedule
import cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase.toService
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class AdminSlotUseCasesSpec {

    private val serviceId = Uuid.random()

    @Test
    fun newServiceFormIsValidOnceTitleAndPriceAreFilled() {
        val form = newServiceForm("Jména a věk dětí", "Poznámka")
        assertEquals(listOf(SlotServiceProblem.TITLE_BLANK), formProblems(form))
        assertTrue(formProblems(form.copy(title = "Hlídání", pricePerHour = 150.0)).isEmpty())
        assertEquals(2, form.customFields.size)
    }

    @Test
    fun newServiceHasNoMinimumAndNoMaximum() {
        val service = newServiceForm("Děti", "Poznámka").copy(title = "Hlídání").toService(serviceId)
        assertEquals(kotlin.time.Duration.ZERO, service.minDuration)
        assertNull(service.maxDuration)
    }

    @Test
    fun zeroMaximumMeansNoLimitAndRoundTrips() {
        val form = SlotServiceForm(title = "Hlídání", stepMinutes = 60, minMinutes = 0, maxMinutes = 0, pricePerHour = 150.0)
        val service = form.toService(serviceId)
        assertEquals(60.minutes, service.step)
        assertNull(service.maxDuration)
        assertTrue(formProblems(form).isEmpty())
        assertEquals(form, formOf(service))
    }

    @Test
    fun maximumShorterThanMinimumIsReported() {
        val form = SlotServiceForm(title = "Hlídání", stepMinutes = 60, minMinutes = 120, maxMinutes = 60)
        assertEquals(listOf(SlotServiceProblem.MAX_DURATION_INVALID), formProblems(form))
    }

    @Test
    fun formRoundTripsAService() {
        val form = SlotServiceForm(
            title = "Hlídání", stepMinutes = 15, minMinutes = 45, maxMinutes = 180, pricePerHour = 120.0,
            capacity = 6, maxSeats = 2, deadlineHours = 2, horizonDays = 14, bankTransfer = false, onSite = true,
            ownerEmails = listOf(" a@b.cz ", ""), isPublished = true,
        )
        val service = form.toService(serviceId)
        assertEquals(15.minutes, service.step)
        assertEquals(45.minutes, service.minDuration)
        assertEquals(2.hours, service.reservationDeadline)
        assertEquals(14.days, service.bookingHorizon)
        assertEquals(listOf(PaymentType.ON_SITE), service.allowedPaymentTypes)
        assertEquals(listOf("a@b.cz"), service.ownerEmails)
        assertEquals(form.copy(ownerEmails = listOf("a@b.cz")), formOf(service))
    }

    @Test
    fun zeroDeadlineMeansNoDeadline() {
        assertNull(SlotServiceForm(title = "x", deadlineHours = 0).toService(serviceId).reservationDeadline)
    }

    private fun rule(day: DayOfWeek, from: Int, to: Int) =
        AvailabilityRule(Uuid.random(), serviceId, day, LocalTime(from, 0), LocalTime(to, 0), LocalDate(2026, 9, 1))

    @Test
    fun scheduleRoundTripsThroughTheDraft() {
        val rules = listOf(rule(DayOfWeek.TUESDAY, 8, 12), rule(DayOfWeek.THURSDAY, 8, 12))
        val exception = AvailabilityException(Uuid.random(), serviceId, LocalDate(2026, 10, 22), closed = false, from = LocalTime(9, 0), to = LocalTime(11, 0))
        val draft = scheduleDraftOf(rules, listOf(exception))

        assertEquals(listOf(WindowDraft("08:00", "12:00")), draft.periods.single().windowsOf(DayOfWeek.TUESDAY))
        val (backRules, backExceptions) = draft.toSchedule(serviceId).getOrNull()!!
        assertEquals(rules.map { it.dayOfWeek to it.from }.toSet(), backRules.map { it.dayOfWeek to it.from }.toSet())
        assertEquals(listOf(exception), backExceptions)
    }

    @Test
    fun unfilledTimeIsIncomplete() {
        val draft = ScheduleDraft(
            periods = listOf(PeriodDraft("2026-09-01", "", mapOf(DayOfWeek.MONDAY to listOf(WindowDraft("08:00", ""))))),
            exceptions = emptyList(),
        )
        assertEquals(listOf(ScheduleProblem.INCOMPLETE), draft.toSchedule(serviceId).leftOrNull())
    }

    @Test
    fun periodWithoutAnyWindowIsIncomplete() {
        val draft = ScheduleDraft(listOf(PeriodDraft("2026-09-01", "", emptyMap())), emptyList())
        assertEquals(listOf(ScheduleProblem.INCOMPLETE), draft.toSchedule(serviceId).leftOrNull())
    }

    @Test
    fun serverChecksRunInTheFormToo() {
        val draft = ScheduleDraft(
            periods = listOf(PeriodDraft("2026-09-01", "", mapOf(DayOfWeek.MONDAY to listOf(WindowDraft("12:00", "08:00"))))),
            exceptions = listOf(ExceptionDraft(Uuid.random(), "2026-10-22", closed = false, from = "", to = "")),
        )
        // Výjimka bez času je nevyplněná dřív, než se dojde ke kontrolám rozvrhu.
        assertEquals(listOf(ScheduleProblem.INCOMPLETE), draft.toSchedule(serviceId).leftOrNull())
        val withTime = draft.withException(0, draft.exceptions[0].copy(closed = true))
        assertEquals(listOf(ScheduleProblem.WINDOW_EMPTY), withTime.toSchedule(serviceId).leftOrNull())
    }

    @Test
    fun addedWindowContinuesWhereThePreviousEnded() {
        val period = PeriodDraft("2026-09-01", "", emptyMap())
            .withAddedWindow(DayOfWeek.TUESDAY)
            .withAddedWindow(DayOfWeek.TUESDAY)
        assertEquals(listOf(WindowDraft("08:00", "12:00"), WindowDraft("12:00", "")), period.windowsOf(DayOfWeek.TUESDAY))
        assertEquals(listOf(WindowDraft("12:00", "")), period.withoutWindow(DayOfWeek.TUESDAY, 0).windowsOf(DayOfWeek.TUESDAY))
    }

    @Test
    fun addedPeriodFollowsTheLastOneAndCopiesItsWeek() {
        val week = mapOf(DayOfWeek.TUESDAY to listOf(WindowDraft("08:00", "12:00")))
        val today = LocalDate(2026, 10, 20)

        val open = ScheduleDraft(listOf(PeriodDraft("2026-09-01", "", week)), emptyList()).withAddedPeriod(today)
        assertEquals(listOf("2026-09-01" to "2026-10-20", "2026-10-21" to ""), open.periods.map { it.validFrom to it.validTo })
        assertEquals(week, open.periods[1].days)

        val ended = ScheduleDraft(listOf(PeriodDraft("2026-09-01", "2027-01-31", week)), emptyList()).withAddedPeriod(today)
        assertEquals("2027-02-01", ended.periods[1].validFrom)

        val first = ScheduleDraft(emptyList(), emptyList()).withAddedPeriod(today)
        assertEquals("2026-10-20", first.periods.single().validFrom)
    }

    private fun row(vs: String?, email: String = "jana+test@example.cz") = SlotReservationRow(
        id = Uuid.random(),
        start = LocalDateTime(2026, 10, 20, 9, 0),
        end = LocalDateTime(2026, 10, 20, 11, 30),
        contactName = "Nováková",
        contactEmail = email,
        contactPhone = null,
        seatCount = 2,
        status = Reservation.Status.CONFIRMED,
        paymentType = PaymentType.BANK_TRANSFER,
        totalPrice = 750.0,
        variableSymbol = vs,
        customValues = emptyMap(),
    )

    @Test
    fun reservationLinkSearchesByVariableSymbolOrEmail() {
        assertEquals("/admin/reservations?search=2610001", adminReservationLink(row("2610001")))
        assertEquals("/admin/reservations?search=jana%2Btest%40example.cz", adminReservationLink(row(null)))
    }

    @Test
    fun dayLabelsAndTotals() {
        assertEquals("9:00–11:30", reservationTimeLabel(row(null)))
        assertEquals(4, dayChildrenTotal(listOf(row(null), row("1"))))
        assertEquals(LocalDate(2026, 11, 1), shiftDay(LocalDate(2026, 10, 31), 1))
    }

    @Test
    fun createdServiceLinkOpensScheduleTab() {
        val id = Uuid.parse("12345678-1234-1234-1234-123456789abc")
        assertEquals("/admin/slots/$id", adminSlotServiceLink(id))
        assertEquals("/admin/slots/$id?tab=schedule", adminSlotServiceLink(id, SlotAdminTab.SCHEDULE))
        assertEquals(SlotAdminTab.SCHEDULE, slotAdminTabOf("schedule"))
        assertEquals(SlotAdminTab.SETTINGS, slotAdminTabOf(" Settings "))
        assertEquals(SlotAdminTab.DAY, slotAdminTabOf(null))
        assertEquals(SlotAdminTab.DAY, slotAdminTabOf("nonsense"))
    }
}
