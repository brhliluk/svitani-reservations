package cz.svitaninymburk.projects.reservations.ui.admin.events.instance

import cz.svitaninymburk.projects.reservations.event.RecurrenceType
import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.EventInstanceCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.buildCreateEventInstanceRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.instanceRecurrencePreviewDates
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private fun sampleCreateForm() = EventInstanceCreateFormData(
    title = "",
    description = "",
    ownerEmails = listOf("a@x.cz", "not-an-email"),
    price = 1500.0,
    capacity = 12,
    waitlistCapacity = 3,
    durationHours = 1,
    durationMinutes = 30,
    allowBankTransfer = true,
    allowOnSite = false,
    showAttendeeCount = true,
    allowMultipleSeats = true,
    customFields = emptyList(),
    deadlineMessage = "",
)

class EventInstanceCreateFormUseCasesSpec {

    @Test
    fun previewDatesEmptyWhenNotRecurring() {
        assertTrue(
            instanceRecurrencePreviewDates("2026-02-01", "18:00", RecurrenceType.NONE, "").isEmpty(),
        )
    }

    @Test
    fun previewDatesEmptyWhenDateOrTimeBlank() {
        assertTrue(
            instanceRecurrencePreviewDates("", "18:00", RecurrenceType.WEEKLY, "2026-03-01").isEmpty(),
        )
    }

    @Test
    fun previewDatesGeneratedForWeeklyRecurrence() {
        val dates = instanceRecurrencePreviewDates("2026-02-01", "18:00", RecurrenceType.WEEKLY, "2026-02-22")
        assertEquals(4, dates.size)
        assertEquals(LocalDateTime(2026, 2, 1, 18, 0), dates.first())
    }

    @Test
    fun buildCreateRequestMapsFieldsFiltersOwnerEmailsAndKeepsBlankTitleNull() {
        val request = buildCreateEventInstanceRequest(
            form = sampleCreateForm(),
            definitionId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            startDateTime = LocalDateTime(2026, 2, 1, 18, 0),
            reservationDeadline = 2.hours,
            isPublished = true,
        )
        assertEquals(null, request.title)
        assertEquals(listOf("a@x.cz"), request.ownerEmails)
        assertEquals(1500.0, request.price)
        assertEquals(1.hours + 30.minutes, request.duration)
        assertEquals(listOf(PaymentInfo.Type.BANK_TRANSFER), request.allowedPaymentTypes)
        assertEquals(2.hours, request.reservationDeadline)
        assertEquals(true, request.isPublished)
    }
}
