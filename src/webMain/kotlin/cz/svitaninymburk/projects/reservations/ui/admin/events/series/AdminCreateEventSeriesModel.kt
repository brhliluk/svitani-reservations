package cz.svitaninymburk.projects.reservations.ui.admin.events.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.localizedMessage
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.EventDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesCreateFormData
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesCreateMutations
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.EventSeriesCreateQueries
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.SeriesCreateValidation
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.SeriesCreateValidationError
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildCreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.buildSeriesLessonConfigs
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeLessonEndTime
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.computeSeriesDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.effectiveSeriesDates
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.keptLessonIndices
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.parseTimeOrNull
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.resolveReservationDeadline
import cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase.validateSeriesCreateForm
import cz.svitaninymburk.projects.reservations.ui.util.ScreenModel
import cz.svitaninymburk.projects.reservations.ui.util.ToastType
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AdminCreateEventSeriesModel(
    scope: CoroutineScope,
    private val queries: EventSeriesCreateQueries,
    private val mutations: EventSeriesCreateMutations,
    private val router: Router,
    private val currentUserEmail: String,
    private val preselectedDefinitionId: String?,
) : ScreenModel(scope) {

    var isLoadingDefinitions by mutableStateOf(true); private set
    var definitions by mutableStateOf<List<EventDefinition>>(emptyList()); private set
    var selectedDefinitionId by mutableStateOf(preselectedDefinitionId); private set

    var startDate by mutableStateOf(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
    ); private set
    var endDate by mutableStateOf("")
    var lessonCount by mutableIntStateOf(1); private set
    var lessonDayOfWeekOrdinal by mutableStateOf<Int?>(null); private set
    var lessonStartTimeStr by mutableStateOf("")
    var lessonDateOverrides by mutableStateOf(mapOf<Int, String>()); private set
    var lessonDropIn by mutableStateOf(mapOf<Int, Boolean>()); private set
    var excludedLessonIndices by mutableStateOf(setOf<Int>()); private set

    var titleOverride by mutableStateOf("")
    var descriptionOverride by mutableStateOf("")
    var ownerEmails by mutableStateOf(listOf(currentUserEmail))
    var priceOverride: Number? by mutableStateOf(0)
    var lessonPriceOverride: Number? by mutableStateOf(null)
    var capacityOverride by mutableIntStateOf(10)
    var waitlistCapacityOverride by mutableIntStateOf(10)
    var allowBankTransfer by mutableStateOf(true)
    var allowOnSite by mutableStateOf(true)
    var showAttendeeCount by mutableStateOf(true)
    var allowMultipleSeats by mutableStateOf(true)

    var deadlineEnabled by mutableStateOf(false)
    var deadlineTypeIsHours by mutableStateOf(true)
    var deadlineHours by mutableIntStateOf(2)
    var deadlineDaysBefore by mutableIntStateOf(1)
    var deadlineTimeStr by mutableStateOf("18:00")
    var deadlineMessage by mutableStateOf("")

    var customFields by mutableStateOf(listOf<CustomFieldDefinition>())

    var isSubmitting by mutableStateOf(false); private set

    val selectedDefinition: EventDefinition?
        get() = definitions.find { it.id.toString() == selectedDefinitionId }

    /** Všechny vygenerované termíny — i vyřazené, aby šly v tabulce vrátit zpět. */
    val computedSeriesDates: List<LocalDate>
        get() = computeSeriesDates(startDate, lessonDayOfWeekOrdinal, lessonCount)

    /** Termíny, které se opravdu vytvoří (po override, bez vyřazených). */
    val effectiveLessonDates: List<LocalDate>
        get() = effectiveSeriesDates(computedSeriesDates, lessonDateOverrides, excludedLessonIndices)

    fun isLessonExcluded(index: Int) = index in excludedLessonIndices

    fun load() {
        scope.launch {
            queries.definitions()
                .onRight { defs ->
                    definitions = defs
                    isLoadingDefinitions = false
                    if (preselectedDefinitionId != null) {
                        defs.find { it.id.toString() == preselectedDefinitionId }?.let { applyDefinitionDefaults(it) }
                    }
                }
                .onLeft { error ->
                    showToast(currentStrings.toastTemplatesLoadError(error.localizedMessage(currentStrings)), ToastType.Error)
                    isLoadingDefinitions = false
                }
        }
    }

    fun onDefinitionSelected(id: String?) {
        selectedDefinitionId = id
        definitions.find { it.id.toString() == id }?.let { applyDefinitionDefaults(it) }
    }

    fun onStartDateChange(v: String) { startDate = v; syncEndDate() }

    fun onLessonCountChange(n: Int) {
        lessonCount = n
        // Nastavení řádků, které se zmenšením počtu ztratily, zahoď — jinak by se
        // po opětovném zvětšení tiše vrátilo (a lekce by nevznikla).
        lessonDateOverrides = lessonDateOverrides.filterKeys { it < n }
        lessonDropIn = lessonDropIn.filterKeys { it < n }
        excludedLessonIndices = excludedLessonIndices.filter { it < n }.toSet()
        syncEndDate()
    }

    fun onLessonDayChange(ordinal: Int?) { lessonDayOfWeekOrdinal = ordinal; syncEndDate() }

    private fun syncEndDate() {
        effectiveLessonDates.lastOrNull()?.let { endDate = it.toString() }
    }

    fun setLessonDateOverride(index: Int, value: String, defaultDate: String) {
        lessonDateOverrides = if (value == defaultDate) lessonDateOverrides - index else lessonDateOverrides + (index to value)
        syncEndDate()
    }

    fun toggleLessonExcluded(index: Int) {
        excludedLessonIndices = if (index in excludedLessonIndices) excludedLessonIndices - index else excludedLessonIndices + index
        syncEndDate()
    }

    fun setAllDropIn(all: Boolean) {
        lessonDropIn = if (all) {
            keptLessonIndices(computedSeriesDates, excludedLessonIndices).associateWith { true }
        } else {
            emptyMap()
        }
    }

    fun setLessonDropIn(index: Int, value: Boolean) {
        lessonDropIn = if (value) lessonDropIn + (index to true) else lessonDropIn - index
    }

    private fun applyDefinitionDefaults(definition: EventDefinition) {
        titleOverride = definition.title
        descriptionOverride = definition.description
        ownerEmails = definition.ownerEmails.ifEmpty { listOf(currentUserEmail) }
        priceOverride = definition.defaultPrice
        capacityOverride = definition.defaultCapacity
        allowBankTransfer = definition.allowedPaymentTypes.contains(PaymentType.BANK_TRANSFER)
        allowOnSite = definition.allowedPaymentTypes.contains(PaymentType.ON_SITE)
        showAttendeeCount = definition.showAttendeeCount
        allowMultipleSeats = definition.allowMultipleSeats
        customFields = definition.customFields
    }

    fun submit(isPublished: Boolean) {
        val definitionId = selectedDefinitionId ?: return
        val validation = validateSeriesCreateForm(startDate, endDate, titleOverride, ownerEmails)
        if (validation is SeriesCreateValidation.Invalid) {
            showToast(validationErrorMessage(validation.error), ToastType.Error)
            return
        }
        val valid = validation as SeriesCreateValidation.Valid

        val generated = computedSeriesDates
        val effectiveDates = effectiveLessonDates
        if (generated.isNotEmpty() && effectiveDates.isEmpty()) {
            showToast(currentStrings.validationAllLessonsExcluded, ToastType.Error)
            return
        }

        val def = selectedDefinition
        val startT = parseTimeOrNull(lessonStartTimeStr)
        val durationMinutes = def?.defaultDuration?.inWholeMinutes?.toInt()
        val endT = if (startT != null && durationMinutes != null) computeLessonEndTime(startT, durationMinutes) else null
        val customLessons = buildSeriesLessonConfigs(generated, lessonDateOverrides, startT, durationMinutes, lessonDropIn, excludedLessonIndices)
        val firstLessonDate = effectiveDates.firstOrNull() ?: valid.startDate
        val deadline = resolveReservationDeadline(
            firstLessonDate, startT, deadlineEnabled, deadlineTypeIsHours, deadlineHours, deadlineDaysBefore, deadlineTimeStr,
        )
        val request = buildCreateEventSeriesRequest(
            form = formData(),
            definitionId = Uuid.parse(definitionId),
            startDate = firstLessonDate,
            endDate = effectiveDates.lastOrNull() ?: valid.endDate,
            lessonCount = effectiveDates.size.takeIf { it > 0 } ?: lessonCount,
            lessonDayOfWeek = lessonDayOfWeekOrdinal?.let { DayOfWeek(it) },
            lessonStartTime = startT,
            lessonEndTime = endT,
            customLessons = customLessons,
            reservationDeadline = deadline,
            isPublished = isPublished,
        )
        run(
            loading = { isSubmitting = it },
            errorMessage = { currentStrings.errorToast(it.localizedMessage(currentStrings)) },
            block = { mutations.create(request) },
            onSuccess = {
                showToast(currentStrings.toastSeriesCreated)
                scope.launch { delay(500); router.navigate("/admin/events") }
            },
        )
    }

    private fun formData() = EventSeriesCreateFormData(
        title = titleOverride,
        description = descriptionOverride,
        ownerEmails = ownerEmails,
        price = priceOverride?.toDouble() ?: 0.0,
        lessonPrice = lessonPriceOverride?.toDouble(),
        capacity = capacityOverride,
        waitlistCapacity = waitlistCapacityOverride,
        allowBankTransfer = allowBankTransfer,
        allowOnSite = allowOnSite,
        showAttendeeCount = showAttendeeCount,
        allowMultipleSeats = allowMultipleSeats,
        customFields = customFields,
        deadlineMessage = deadlineMessage,
    )

    private fun validationErrorMessage(error: SeriesCreateValidationError): String = when (error) {
        SeriesCreateValidationError.MissingDates -> currentStrings.validationDatesRequired
        SeriesCreateValidationError.MissingTitle -> currentStrings.validationSeriesTitleRequired
        SeriesCreateValidationError.MissingOwnerEmail -> currentStrings.validationOwnerEmailRequired
        SeriesCreateValidationError.StartDateFormat -> currentStrings.validationStartDateFormat
        SeriesCreateValidationError.EndDateFormat -> currentStrings.validationEndDateFormat
        SeriesCreateValidationError.EndBeforeStart -> currentStrings.validationEndBeforeStart
    }
}

fun IComponent.buildAdminCreateEventSeriesModel(
    scope: CoroutineScope,
    router: Router,
    currentUserEmail: String,
    preselectedDefinitionId: String?,
): AdminCreateEventSeriesModel {
    val event = getService<EventServiceInterface>(RpcSerializersModules)
    val admin = getService<AdminServiceInterface>(RpcSerializersModules)
    return AdminCreateEventSeriesModel(
        scope = scope,
        queries = EventSeriesCreateQueries(event),
        mutations = EventSeriesCreateMutations(admin),
        router = router,
        currentUserEmail = currentUserEmail,
        preselectedDefinitionId = preselectedDefinitionId,
    )
}
