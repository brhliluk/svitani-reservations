package cz.svitaninymburk.projects.reservations.ui.admin.events.series

import cz.svitaninymburk.projects.reservations.reservation.PaymentInfo
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.SeriesCreateValidation
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.SeriesCreateValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildCreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildSeriesLessonConfigs
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeLessonEndTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeSeriesDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.effectiveSeriesDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.keptLessonIndices
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.validateSeriesCreateForm
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun sampleForm() = EventSeriesCreateFormData(
    title = "Kurz jógy",
    description = "Popis",
    ownerEmails = listOf("a@x.cz", "not-an-email"),
    price = 1500.0,
    lessonPrice = null,
    capacity = 12,
    waitlistCapacity = 3,
    allowBankTransfer = true,
    allowOnSite = false,
    showAttendeeCount = true,
    customFields = emptyList(),
    deadlineMessage = "",
)

class EventSeriesCreateFormUseCasesSpec {

    @Test
    fun computeSeriesDatesEmptyWhenNoDaySelected() {
        assertTrue(computeSeriesDates("2026-02-01", null, 4).isEmpty())
    }

    @Test
    fun computeSeriesDatesEmptyWhenLessonCountNonPositive() {
        assertTrue(computeSeriesDates("2026-02-01", DayOfWeek.MONDAY.isoDayNumber, 0).isEmpty())
    }

    @Test
    fun computeSeriesDatesAdvancesToFirstMatchingDayThenWeekly() {
        // 2026-02-01 is a Sunday; first Monday on/after is 2026-02-02, then weekly.
        val dates = computeSeriesDates("2026-02-01", DayOfWeek.MONDAY.isoDayNumber, 3)
        assertEquals(listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 9), LocalDate(2026, 2, 16)), dates)
    }

    @Test
    fun computeLessonEndTimeAddsDurationWrappingMidnight() {
        assertEquals(LocalTime(19, 30), computeLessonEndTime(LocalTime(18, 0), 90))
    }

    @Test
    fun validateRejectsBlankEndDateThenBlankTitleThenOwnerEmail() {
        assertEquals(SeriesCreateValidationError.MissingDates, (validateSeriesCreateForm("2026-02-02", "", "T", listOf("a@x.cz")) as SeriesCreateValidation.Invalid).error)
        assertEquals(SeriesCreateValidationError.MissingTitle, (validateSeriesCreateForm("2026-02-02", "2026-02-16", "  ", listOf("a@x.cz")) as SeriesCreateValidation.Invalid).error)
        assertEquals(SeriesCreateValidationError.MissingOwnerEmail, (validateSeriesCreateForm("2026-02-02", "2026-02-16", "T", listOf("nope")) as SeriesCreateValidation.Invalid).error)
    }

    @Test
    fun validateRejectsEndBeforeStart() {
        assertEquals(SeriesCreateValidationError.EndBeforeStart, (validateSeriesCreateForm("2026-02-16", "2026-02-02", "T", listOf("a@x.cz")) as SeriesCreateValidation.Invalid).error)
    }

    @Test
    fun validateReturnsParsedDatesWhenValid() {
        val v = validateSeriesCreateForm("2026-02-02", "2026-02-16", "T", listOf("a@x.cz")) as SeriesCreateValidation.Valid
        assertEquals(LocalDate(2026, 2, 2), v.startDate)
        assertEquals(LocalDate(2026, 2, 16), v.endDate)
    }

    @Test
    fun buildLessonConfigsNullWhenNoStartTimeOrNoDuration() {
        assertNull(buildSeriesLessonConfigs(listOf(LocalDate(2026, 2, 2)), emptyMap(), null, 60, emptyMap()))
        assertNull(buildSeriesLessonConfigs(listOf(LocalDate(2026, 2, 2)), emptyMap(), LocalTime(18, 0), null, emptyMap()))
        assertNull(buildSeriesLessonConfigs(emptyList(), emptyMap(), LocalTime(18, 0), 60, emptyMap()))
    }

    @Test
    fun buildLessonConfigsAppliesOverridesAndDropIn() {
        val dates = listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 9))
        val configs = buildSeriesLessonConfigs(dates, mapOf(1 to "2026-02-10"), LocalTime(18, 0), 60, mapOf(0 to true))!!
        assertEquals(2, configs.size)
        assertEquals(LocalDate(2026, 2, 2), configs[0].startDateTime.date)
        assertEquals(true, configs[0].isDropIn)
        assertEquals(LocalDate(2026, 2, 10), configs[1].startDateTime.date)   // override applied
        assertEquals(LocalTime(19, 0), configs[0].endDateTime.time)           // 18:00 + 60min
        assertEquals(false, configs[1].isDropIn)
    }

    @Test
    fun buildLessonConfigsSkipsExcludedIndicesKeepingSettingsOfTheRest() {
        val dates = listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 9), LocalDate(2026, 2, 16))
        val configs = buildSeriesLessonConfigs(
            dates = dates,
            lessonDateOverrides = mapOf(2 to "2026-02-17"),
            lessonStartTime = LocalTime(18, 0),
            durationMinutes = 60,
            lessonDropIn = mapOf(2 to true),
            excludedIndices = setOf(1),
        )!!
        // The middle date is gone; override and drop-in stay pinned to original index 2.
        assertEquals(2, configs.size)
        assertEquals(LocalDate(2026, 2, 2), configs[0].startDateTime.date)
        assertEquals(false, configs[0].isDropIn)
        assertEquals(LocalDate(2026, 2, 17), configs[1].startDateTime.date)
        assertEquals(true, configs[1].isDropIn)
    }

    @Test
    fun buildLessonConfigsNullWhenEveryDateExcluded() {
        val dates = listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 9))
        assertNull(buildSeriesLessonConfigs(dates, emptyMap(), LocalTime(18, 0), 60, emptyMap(), setOf(0, 1)))
    }

    @Test
    fun effectiveSeriesDatesDropsExcludedAppliesOverridesAndSorts() {
        val dates = listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 9), LocalDate(2026, 2, 16))
        assertEquals(
            listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 4)),
            effectiveSeriesDates(dates, mapOf(2 to "2026-02-04"), setOf(1)),
        )
    }

    @Test
    fun effectiveSeriesDatesEmptyWhenAllExcluded() {
        val dates = listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 9))
        assertTrue(effectiveSeriesDates(dates, emptyMap(), setOf(0, 1)).isEmpty())
    }

    @Test
    fun keptLessonIndicesIgnoresStaleOutOfRangeExclusions() {
        val dates = listOf(LocalDate(2026, 2, 2), LocalDate(2026, 2, 9))
        assertEquals(listOf(0), keptLessonIndices(dates, setOf(1, 7)))
    }

    @Test
    fun buildRequestMapsFieldsAndFiltersOwnerEmails() {
        val request = buildCreateEventSeriesRequest(
            form = sampleForm(),
            definitionId = kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-000000000001"),
            startDate = LocalDate(2026, 2, 2),
            endDate = LocalDate(2026, 2, 16),
            lessonCount = 3,
            lessonDayOfWeek = DayOfWeek.MONDAY,
            lessonStartTime = LocalTime(18, 0),
            lessonEndTime = LocalTime(19, 0),
            customLessons = null,
            reservationDeadline = null,
            isPublished = true,
        )
        assertEquals("Kurz jógy", request.title)
        assertEquals(listOf("a@x.cz"), request.ownerEmails)
        assertEquals(1500.0, request.price)
        assertEquals(3, request.lessonCount)
        assertEquals(listOf(PaymentInfo.Type.BANK_TRANSFER), request.allowedPaymentTypes)
        assertEquals(true, request.isPublished)
    }

    @Test
    fun buildRequestCarriesLessonPrice() {
        val request = buildRequestFrom(sampleForm().copy(lessonPrice = 250.0))
        assertEquals(250.0, request.lessonPrice)
    }

    @Test
    fun buildRequestTreatsBlankOrZeroLessonPriceAsUnset() {
        assertNull(buildRequestFrom(sampleForm().copy(lessonPrice = null)).lessonPrice)
        assertNull(buildRequestFrom(sampleForm().copy(lessonPrice = 0.0)).lessonPrice)
    }
}

private fun buildRequestFrom(form: EventSeriesCreateFormData) = buildCreateEventSeriesRequest(
    form = form,
    definitionId = kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-000000000001"),
    startDate = LocalDate(2026, 2, 2),
    endDate = LocalDate(2026, 2, 16),
    lessonCount = 3,
    lessonDayOfWeek = DayOfWeek.MONDAY,
    lessonStartTime = LocalTime(18, 0),
    lessonEndTime = LocalTime(19, 0),
    customLessons = null,
    reservationDeadline = null,
    isPublished = true,
)
