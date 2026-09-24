package cz.svitaninymburk.projects.reservations.ui.admin.events.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.RecurrenceType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.CourseStartDate
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateType
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.EventCreateValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.SingleEventDateTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.allLessonsExcluded
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.buildCreateEventAndInstancesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.buildCreateEventAndSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.parseCourseStartDate
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.parseSingleEventDateTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.create.usecase.validateEventCreateCommon
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.instanceRecurrencePreviewDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.instance.usecase.resolveReservationDeadline
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildSeriesLessonConfigs
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeLessonEndTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeSeriesDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.effectiveSeriesDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.keptLessonIndices
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.parseTimeOrNull
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Zakládání akce "od nuly" — šablona, termíny i rozpis lekcí vznikají jedním
 * odesláním. Podle [eventType] se pak volá jiné RPC: jednorázová i opakovaná
 * akce jsou instance nad novou šablonou, kurz je série lekcí.
 */
class AdminCreateEventModel(
    scope: CoroutineScope,
    private val mutations: EventCreateMutations,
    private val router: Router,
    currentUserEmail: String,
) : ScreenModel(scope) {

    private val today get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    var eventType by mutableStateOf(EventCreateType.SINGLE); private set

    // Definice akce
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var ownerEmails by mutableStateOf(listOf(currentUserEmail))
    var price: Number? by mutableStateOf(0)
    var capacity by mutableIntStateOf(10)
    var waitlistCapacity by mutableIntStateOf(10)
    var durationHours by mutableIntStateOf(1)
    var durationMinutes by mutableIntStateOf(0)
    var allowBankTransfer by mutableStateOf(true)
    var allowOnSite by mutableStateOf(true)
    var showAttendeeCount by mutableStateOf(true)
    var allowMultipleSeats by mutableStateOf(true)
    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())

    // Jednorázová i opakovaná akce
    var startDate by mutableStateOf(today)
    var startTime by mutableStateOf("")

    // Jen opakovaná akce
    var recurrenceType by mutableStateOf(RecurrenceType.WEEKLY)
    var recurrenceEndDateStr by mutableStateOf("")

    // Jen kurz
    var courseStartDate by mutableStateOf(today); private set
    var lessonCount by mutableIntStateOf(1); private set
    var courseLessonDayOrdinal by mutableStateOf<Int?>(null); private set
    var courseLessonStartTimeStr by mutableStateOf("")
    var courseLessonPrice: Number? by mutableStateOf(null)
    var courseLessonRefundAmount: Number? by mutableStateOf(null)
    var lessonDateOverrides by mutableStateOf(mapOf<Int, String>()); private set
    var lessonDropIn by mutableStateOf(mapOf<Int, Boolean>()); private set
    var excludedLessonIndices by mutableStateOf(setOf<Int>()); private set

    // Uzávěrka rezervací
    var deadlineEnabled by mutableStateOf(false)
    var deadlineTypeIsHours by mutableStateOf(true)
    var deadlineHours by mutableIntStateOf(2)
    var deadlineDaysBefore by mutableIntStateOf(1)
    var deadlineTimeStr by mutableStateOf("18:00")
    var deadlineMessage by mutableStateOf("")

    var isSubmitting by mutableStateOf(false); private set

    /** Termíny opakované akce; prázdné, dokud nejsou vyplněná všechna tři pole. */
    val previewDates: List<LocalDateTime>
        get() = if (eventType != EventCreateType.RECURRING) {
            emptyList()
        } else {
            instanceRecurrencePreviewDates(startDate, startTime, recurrenceType, recurrenceEndDateStr)
        }

    /** Všechny vygenerované termíny lekcí — i vyřazené, aby šly v tabulce vrátit zpět. */
    val computedCourseDates: List<LocalDate>
        get() = computeSeriesDates(courseStartDate, courseLessonDayOrdinal, lessonCount)

    /** Termíny, které se opravdu vytvoří (po override, bez vyřazených). */
    val effectiveCourseDates: List<LocalDate>
        get() = effectiveSeriesDates(computedCourseDates, lessonDateOverrides, excludedLessonIndices)

    val courseLessonStartTime: LocalTime? get() = parseTimeOrNull(courseLessonStartTimeStr)

    /** Kolik lekcí kurz dostane — bez dne v týdnu termíny nevzniknou a platí zadaný počet (viz submitCourse). */
    val courseLessonCountForRefundPreview: Int
        get() = effectiveCourseDates.size.takeIf { it > 0 } ?: lessonCount

    fun isLessonExcluded(index: Int) = index in excludedLessonIndices

    fun onEventTypeChange(type: EventCreateType) { eventType = type }

    /**
     * Změna data začátku, počtu lekcí nebo dne v týdnu přepočítá celý rozpis,
     * takže ruční úpravy řádků by po ní ukazovaly na jiné termíny — zahoď je.
     */
    fun onCourseStartDateChange(value: String) { courseStartDate = value; resetLessonEdits() }

    fun onLessonCountChange(count: Int) { lessonCount = count; resetLessonEdits() }

    fun onCourseLessonDayChange(ordinal: Int?) { courseLessonDayOrdinal = ordinal; resetLessonEdits() }

    private fun resetLessonEdits() {
        lessonDateOverrides = emptyMap()
        excludedLessonIndices = emptySet()
    }

    fun setLessonDateOverride(index: Int, value: String, defaultDate: String) {
        lessonDateOverrides = if (value == defaultDate) lessonDateOverrides - index else lessonDateOverrides + (index to value)
    }

    fun toggleLessonExcluded(index: Int) {
        excludedLessonIndices = if (index in excludedLessonIndices) excludedLessonIndices - index else excludedLessonIndices + index
    }

    fun setLessonDropIn(index: Int, value: Boolean) {
        lessonDropIn = if (value) lessonDropIn + (index to true) else lessonDropIn - index
    }

    fun setAllDropIn(all: Boolean) {
        lessonDropIn = if (all) {
            keptLessonIndices(computedCourseDates, excludedLessonIndices).associateWith { true }
        } else {
            emptyMap()
        }
    }

    fun submit(isPublished: Boolean) {
        validateEventCreateCommon(title, ownerEmails)?.let { return showValidationError(it) }
        when (eventType) {
            EventCreateType.SINGLE -> submitSingle(isPublished)
            EventCreateType.RECURRING -> submitRecurring(isPublished)
            EventCreateType.COURSE -> submitCourse(isPublished)
        }
    }

    private fun submitSingle(isPublished: Boolean) {
        val dateTime = when (val parsed = parseSingleEventDateTime(startDate, startTime)) {
            is SingleEventDateTime.Invalid -> return showValidationError(parsed.error)
            is SingleEventDateTime.Valid -> parsed.dateTime
        }
        createInstances(
            dateTimes = listOf(dateTime),
            isPublished = isPublished,
            successMessage = currentStrings.toastEventCreated,
        )
    }

    private fun submitRecurring(isPublished: Boolean) {
        val dates = previewDates
        if (dates.isEmpty()) return showValidationError(EventCreateValidationError.NoRecurrenceDates)
        createInstances(
            dateTimes = dates,
            isPublished = isPublished,
            successMessage = currentStrings.toastEventsCreated(dates.size),
        )
    }

    private fun createInstances(dateTimes: List<LocalDateTime>, isPublished: Boolean, successMessage: String) {
        val request = buildCreateEventAndInstancesRequest(
            form = formData(),
            dateTimes = dateTimes,
            reservationDeadline = deadlineFor(dateTimes.first()),
            isPublished = isPublished,
        )
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.createWithInstances(request) },
            onSuccess = { navigateToEvents(successMessage) },
        )
    }

    private fun submitCourse(isPublished: Boolean) {
        val parsedStart = when (val parsed = parseCourseStartDate(courseStartDate)) {
            is CourseStartDate.Invalid -> return showValidationError(parsed.error)
            is CourseStartDate.Valid -> parsed.date
        }
        val generated = computedCourseDates
        val effective = effectiveCourseDates
        if (allLessonsExcluded(generated, effective)) {
            return showValidationError(EventCreateValidationError.AllLessonsExcluded)
        }

        val form = formData()
        val lessonStartTime = courseLessonStartTime
        val lessonEndTime = lessonStartTime?.let { computeLessonEndTime(it, form.totalDurationMinutes) }
        val firstLessonDate = effective.firstOrNull() ?: parsedStart
        val request = buildCreateEventAndSeriesRequest(
            form = form,
            startDate = firstLessonDate,
            endDate = effective.lastOrNull() ?: parsedStart,
            lessonCount = effective.size.takeIf { it > 0 } ?: lessonCount,
            lessonDayOfWeek = courseLessonDayOrdinal?.let { DayOfWeek(it) },
            lessonStartTime = lessonStartTime,
            lessonEndTime = lessonEndTime,
            customLessons = buildSeriesLessonConfigs(
                dates = generated,
                lessonDateOverrides = lessonDateOverrides,
                lessonStartTime = lessonStartTime,
                durationMinutes = form.totalDurationMinutes,
                lessonDropIn = lessonDropIn,
                excludedIndices = excludedLessonIndices,
            ),
            lessonPrice = courseLessonPrice?.toDouble(),
            lessonRefundAmount = courseLessonRefundAmount?.toDouble(),
            reservationDeadline = deadlineFor(LocalDateTime(firstLessonDate, lessonStartTime ?: LocalTime(0, 0))),
            isPublished = isPublished,
        )
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.createWithSeries(request) },
            onSuccess = { navigateToEvents(currentStrings.toastCourseCreated) },
        )
    }

    private fun deadlineFor(startDateTime: LocalDateTime) = resolveReservationDeadline(
        startDt = startDateTime,
        enabled = deadlineEnabled,
        typeIsHours = deadlineTypeIsHours,
        hours = deadlineHours,
        daysBefore = deadlineDaysBefore,
        timeStr = deadlineTimeStr,
    )

    private fun navigateToEvents(message: String) {
        showToast(message)
        scope.launch { delay(500); router.navigate("/admin/events") }
    }

    private fun formData() = EventCreateFormData(
        title = title,
        description = description,
        ownerEmails = ownerEmails,
        price = price?.toDouble() ?: 0.0,
        capacity = capacity,
        waitlistCapacity = waitlistCapacity,
        durationHours = durationHours,
        durationMinutes = durationMinutes,
        allowBankTransfer = allowBankTransfer,
        allowOnSite = allowOnSite,
        showAttendeeCount = showAttendeeCount,
        allowMultipleSeats = allowMultipleSeats,
        customFields = customFields,
        deadlineMessage = deadlineMessage,
    )

    private fun showValidationError(error: EventCreateValidationError) {
        showToast(validationErrorMessage(error), ToastType.Error)
    }

    private fun validationErrorMessage(error: EventCreateValidationError): String = when (error) {
        EventCreateValidationError.MissingTitle -> currentStrings.validationTitleRequired
        EventCreateValidationError.MissingOwnerEmail -> currentStrings.validationOwnerEmailRequired
        EventCreateValidationError.MissingDateOrTime -> currentStrings.validationDatesOrTimeRequired
        EventCreateValidationError.DateTimeFormat -> currentStrings.validationDateTimeFormat
        EventCreateValidationError.NoRecurrenceDates -> currentStrings.validationNoDates
        EventCreateValidationError.MissingCourseStartDate -> currentStrings.validationCourseDatesRequired
        EventCreateValidationError.CourseStartDateFormat -> currentStrings.validationStartDateFormat
        EventCreateValidationError.AllLessonsExcluded -> currentStrings.validationAllLessonsExcluded
    }
}

fun IComponent.buildAdminCreateEventModel(
    scope: CoroutineScope,
    router: Router,
    currentUserEmail: String,
): AdminCreateEventModel {
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminCreateEventModel(
        scope = scope,
        mutations = EventCreateMutations(admin),
        router = router,
        currentUserEmail = currentUserEmail,
    )
}
