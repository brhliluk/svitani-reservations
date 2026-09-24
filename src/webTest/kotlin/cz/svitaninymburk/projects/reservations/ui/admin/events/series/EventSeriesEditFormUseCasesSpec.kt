package cz.svitaninymburk.projects.reservations.ui.admin.events.series

import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesEditFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.SeriesFormValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildUpdateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.resolveReservationDeadline
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.validateSeriesForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

private fun sampleForm() = EventSeriesEditFormData(
    title = "Jóga",
    description = "Popis",
    ownerEmails = listOf("a@x.cz", "not-an-email"),
    price = 1500.0,
    lessonPrice = null,
    capacity = 12,
    waitlistCapacity = 3,
    allowBankTransfer = true,
    allowOnSite = false,
    showAttendeeCount = true,
    allowMultipleSeats = true,
    lessonRefundAmount = 100.0,
    customFields = emptyList(),
    deadlineMessage = "",
)

class EventSeriesEditFormUseCasesSpec {

    @Test
    fun validateSeriesFormRequiresTitle() {
        assertEquals(SeriesFormValidationError.MissingTitle, validateSeriesForm("  ", listOf("a@x.cz")))
    }

    @Test
    fun validateSeriesFormRequiresAtLeastOneValidOwnerEmail() {
        assertEquals(SeriesFormValidationError.MissingOwnerEmail, validateSeriesForm("Jóga", listOf("not-an-email")))
    }

    @Test
    fun validateSeriesFormPassesWithTitleAndOwnerEmail() {
        assertNull(validateSeriesForm("Jóga", listOf("a@x.cz")))
    }

    @Test
    fun resolveReservationDeadlineReturnsNullWhenDisabled() {
        assertNull(
            resolveReservationDeadline(
                startDate = LocalDate(2026, 1, 10),
                lessonStartTime = LocalTime(18, 0),
                enabled = false,
                typeIsHours = true,
                hours = 2,
                daysBefore = 1,
                timeStr = "18:00",
            ),
        )
    }

    @Test
    fun resolveReservationDeadlineReturnsHoursWhenTypeIsHours() {
        assertEquals(
            2.hours,
            resolveReservationDeadline(
                startDate = LocalDate(2026, 1, 10),
                lessonStartTime = LocalTime(18, 0),
                enabled = true,
                typeIsHours = true,
                hours = 2,
                daysBefore = 1,
                timeStr = "18:00",
            ),
        )
    }

    @Test
    fun resolveReservationDeadlineReturnsNullForUnparsableTime() {
        assertNull(
            resolveReservationDeadline(
                startDate = LocalDate(2026, 1, 10),
                lessonStartTime = LocalTime(18, 0),
                enabled = true,
                typeIsHours = false,
                hours = 2,
                daysBefore = 1,
                timeStr = "not-a-time",
            ),
        )
    }

    @Test
    fun buildUpdateEventSeriesRequestMapsFormFieldsAndFiltersOwnerEmails() {
        val request = buildUpdateEventSeriesRequest(sampleForm(), reservationDeadline = 2.hours)
        assertEquals("Jóga", request.title)
        assertEquals(listOf("a@x.cz"), request.ownerEmails)
        assertEquals(1500.0, request.price)
        assertEquals(12, request.capacity)
        assertEquals(3, request.waitlistCapacity)
        assertEquals(listOf(PaymentType.BANK_TRANSFER), request.allowedPaymentTypes)
        assertEquals(true, request.showAttendeeCount)
        assertEquals(100.0, request.lessonRefundAmount)
        assertEquals(2.hours, request.reservationDeadline)
    }

    @Test
    fun buildUpdateEventSeriesRequestKeepsZeroLessonRefundAmountAsNoRefund() {
        // Prázdné pole = poměrný kredit, 0 = kurz za lekce nic nevrací — nula se nesmí ztratit.
        val request = buildUpdateEventSeriesRequest(sampleForm().copy(lessonRefundAmount = 0.0), reservationDeadline = null)
        assertEquals(0.0, request.lessonRefundAmount)
    }

    @Test
    fun buildUpdateEventSeriesRequestDropsNegativeLessonRefundAmount() {
        val request = buildUpdateEventSeriesRequest(sampleForm().copy(lessonRefundAmount = -10.0), reservationDeadline = null)
        assertNull(request.lessonRefundAmount)
    }

    @Test
    fun buildUpdateEventSeriesRequestCarriesLessonPrice() {
        val request = buildUpdateEventSeriesRequest(sampleForm().copy(lessonPrice = 250.0), reservationDeadline = null)
        assertEquals(250.0, request.lessonPrice)
    }

    @Test
    fun buildUpdateEventSeriesRequestTreatsBlankOrZeroLessonPriceAsUnset() {
        assertNull(buildUpdateEventSeriesRequest(sampleForm().copy(lessonPrice = null), reservationDeadline = null).lessonPrice)
        assertNull(buildUpdateEventSeriesRequest(sampleForm().copy(lessonPrice = 0.0), reservationDeadline = null).lessonPrice)
    }
}
