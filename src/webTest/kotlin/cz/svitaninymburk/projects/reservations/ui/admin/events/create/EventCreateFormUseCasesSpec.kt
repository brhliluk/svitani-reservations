package cz.svitaninymburk.projects.reservations.ui.admin.events.create

import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.CourseStartDate
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.SingleEventDateTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.allLessonsExcluded
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.buildCreateEventAndInstancesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.buildCreateEventAndSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.parseCourseStartDate
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.parseSingleEventDateTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.validateEventCreateCommon
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private fun sampleForm(
    title: String = "Jóga pro maminky",
    ownerEmails: List<String> = listOf("lektor@svitani.cz", "nesmysl"),
    deadlineMessage: String = "",
) = EventCreateFormData(
    title = title,
    description = "Popis",
    ownerEmails = ownerEmails,
    price = 300.0,
    capacity = 12,
    waitlistCapacity = 4,
    durationHours = 1,
    durationMinutes = 30,
    allowBankTransfer = true,
    allowOnSite = false,
    showAttendeeCount = true,
    allowMultipleSeats = false,
    customFields = emptyList(),
    deadlineMessage = deadlineMessage,
)

class EventCreateFormUseCasesSpec {

    @Test
    fun durationCombinesHoursAndMinutes() {
        assertEquals(1.hours + 30.minutes, sampleForm().duration)
        assertEquals(90, sampleForm().totalDurationMinutes)
    }

    @Test
    fun allowedPaymentTypesFollowCheckboxes() {
        assertEquals(listOf(PaymentType.BANK_TRANSFER), sampleForm().allowedPaymentTypes)
        assertTrue(sampleForm().copy(allowBankTransfer = false).allowedPaymentTypes.isEmpty())
    }

    @Test
    fun commonValidationPassesForFilledForm() {
        assertNull(validateEventCreateCommon("Jóga", listOf("a@x.cz")))
    }

    @Test
    fun commonValidationRejectsBlankTitle() {
        assertEquals(EventCreateValidationError.MissingTitle, validateEventCreateCommon("   ", listOf("a@x.cz")))
    }

    @Test
    fun commonValidationRejectsFormWithoutAnyValidOwnerEmail() {
        assertEquals(
            EventCreateValidationError.MissingOwnerEmail,
            validateEventCreateCommon("Jóga", listOf("", "nesmysl")),
        )
    }

    @Test
    fun singleDateTimeReportsMissingAndMalformedSeparately() {
        assertEquals(
            EventCreateValidationError.MissingDateOrTime,
            (parseSingleEventDateTime("", "10:00") as SingleEventDateTime.Invalid).error,
        )
        assertEquals(
            EventCreateValidationError.MissingDateOrTime,
            (parseSingleEventDateTime("2026-03-01", "") as SingleEventDateTime.Invalid).error,
        )
        assertEquals(
            EventCreateValidationError.DateTimeFormat,
            (parseSingleEventDateTime("1. 3. 2026", "10:00") as SingleEventDateTime.Invalid).error,
        )
    }

    @Test
    fun singleDateTimeCombinesDateAndTime() {
        val parsed = parseSingleEventDateTime("2026-03-01", "10:15") as SingleEventDateTime.Valid
        assertEquals(LocalDateTime(2026, 3, 1, 10, 15), parsed.dateTime)
    }

    @Test
    fun courseStartDateReportsMissingAndMalformedSeparately() {
        assertEquals(
            EventCreateValidationError.MissingCourseStartDate,
            (parseCourseStartDate("  ") as CourseStartDate.Invalid).error,
        )
        assertEquals(
            EventCreateValidationError.CourseStartDateFormat,
            (parseCourseStartDate("2026-13-01") as CourseStartDate.Invalid).error,
        )
        assertEquals(LocalDate(2026, 3, 1), (parseCourseStartDate("2026-03-01") as CourseStartDate.Valid).date)
    }

    @Test
    fun allLessonsExcludedOnlyWhenSomeWereGenerated() {
        // Bez vybraného dne se žádné termíny negenerují — kurz jde založit bez rozpisu.
        assertTrue(!allLessonsExcluded(emptyList(), emptyList()))
        assertTrue(allLessonsExcluded(listOf(LocalDate(2026, 3, 2)), emptyList()))
        assertTrue(!allLessonsExcluded(listOf(LocalDate(2026, 3, 2)), listOf(LocalDate(2026, 3, 2))))
    }

    @Test
    fun instancesRequestKeepsOnlyValidOwnerEmailsAndDropsBlankDeadlineMessage() {
        val request = buildCreateEventAndInstancesRequest(
            form = sampleForm(),
            dateTimes = listOf(LocalDateTime(2026, 3, 1, 10, 0)),
            reservationDeadline = 2.hours,
            isPublished = true,
        )
        assertEquals(listOf("lektor@svitani.cz"), request.ownerEmails)
        assertNull(request.reservationDeadlineMessage)
        assertEquals(1.hours + 30.minutes, request.defaultDuration)
        assertEquals(listOf(PaymentType.BANK_TRANSFER), request.allowedPaymentTypes)
        assertEquals(2.hours, request.reservationDeadline)
        assertTrue(request.isPublished)
    }

    @Test
    fun instancesRequestKeepsFilledDeadlineMessage() {
        val request = buildCreateEventAndInstancesRequest(
            form = sampleForm(deadlineMessage = "Přihlaste se den předem"),
            dateTimes = listOf(LocalDateTime(2026, 3, 1, 10, 0)),
            reservationDeadline = null,
            isPublished = false,
        )
        assertEquals("Přihlaste se den předem", request.reservationDeadlineMessage)
    }

    @Test
    fun seriesRequestDropsNonPositiveLessonPrice() {
        val request = buildCreateEventAndSeriesRequest(
            form = sampleForm(),
            startDate = LocalDate(2026, 3, 2),
            endDate = LocalDate(2026, 3, 23),
            lessonCount = 4,
            customLessons = null,
            lessonPrice = 0.0,
            reservationDeadline = null,
            isPublished = false,
        )
        assertNull(request.lessonPrice)
        assertEquals(4, request.lessonCount)
        assertEquals(LocalDate(2026, 3, 2), request.startDate)
        assertEquals(LocalDate(2026, 3, 23), request.endDate)
    }

    @Test
    fun seriesRequestKeepsPositiveLessonPrice() {
        val request = buildCreateEventAndSeriesRequest(
            form = sampleForm(),
            startDate = LocalDate(2026, 3, 2),
            endDate = LocalDate(2026, 3, 23),
            lessonCount = 4,
            customLessons = null,
            lessonPrice = 120.0,
            reservationDeadline = null,
            isPublished = false,
        )
        assertEquals(120.0, request.lessonPrice)
    }

    /**
     * Server dřív bez rozpisu lekcí neměl z čeho kurz poskládat a pořadník
     * i kredit za omluvenku zahodil — formulář je teď musí poslat.
     */
    @Test
    fun seriesRequestCarriesScheduleWaitlistAndRefund() {
        val request = buildCreateEventAndSeriesRequest(
            form = sampleForm(),
            startDate = LocalDate(2026, 3, 2),
            endDate = LocalDate(2026, 3, 23),
            lessonCount = 4,
            customLessons = null,
            lessonPrice = null,
            reservationDeadline = null,
            isPublished = false,
            lessonDayOfWeek = DayOfWeek.MONDAY,
            lessonStartTime = LocalTime(17, 0),
            lessonEndTime = LocalTime(18, 30),
            lessonRefundAmount = 80.0,
        )
        assertEquals(4, request.defaultWaitlistCapacity)
        assertEquals(DayOfWeek.MONDAY, request.lessonDayOfWeek)
        assertEquals(LocalTime(17, 0), request.lessonStartTime)
        assertEquals(LocalTime(18, 30), request.lessonEndTime)
        assertEquals(80.0, request.lessonRefundAmount)
    }

    @Test
    fun seriesRequestDropsNonPositiveRefund() {
        val request = buildCreateEventAndSeriesRequest(
            form = sampleForm(),
            startDate = LocalDate(2026, 3, 2),
            endDate = LocalDate(2026, 3, 23),
            lessonCount = 4,
            customLessons = null,
            lessonPrice = null,
            reservationDeadline = null,
            isPublished = false,
            lessonRefundAmount = 0.0,
        )
        assertNull(request.lessonRefundAmount)
    }

    @Test
    fun instancesRequestCarriesWaitlistCapacity() {
        val request = buildCreateEventAndInstancesRequest(
            form = sampleForm(),
            dateTimes = listOf(LocalDateTime(2026, 3, 1, 10, 0)),
            reservationDeadline = null,
            isPublished = false,
        )
        assertEquals(4, request.defaultWaitlistCapacity)
    }
}
