package cz.svitaninymburk.projects.reservations.ui.admin.events.series.usecase

import cz.svitaninymburk.projects.reservations.event.CreateEventSeriesRequest
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.LessonConfig
import cz.svitaninymburk.projects.reservations.event.parseOwnerEmails
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.service.AdminServiceInterface
import cz.svitaninymburk.projects.reservations.service.EventServiceInterface
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.uuid.Uuid

// --- Pure helpery (testovatelné bez RPC) ---

data class EventSeriesCreateFormData(
    val title: String,
    val description: String,
    val ownerEmails: List<String>,
    val price: Double,
    val lessonPrice: Double?,
    val capacity: Int,
    val waitlistCapacity: Int,
    val allowBankTransfer: Boolean,
    val allowOnSite: Boolean,
    val showAttendeeCount: Boolean,
    val allowMultipleSeats: Boolean,
    val customFields: List<CustomFieldDefinition>,
    val deadlineMessage: String,
)

/** Data lekcí: od startDate se posune na první výskyt zvoleného dne, pak týdně × lessonCount. */
fun computeSeriesDates(startDate: String, lessonDayOfWeekOrdinal: Int?, lessonCount: Int): List<LocalDate> {
    val dayOrdinal = lessonDayOfWeekOrdinal ?: return emptyList()
    val startD = try { LocalDate.parse(startDate) } catch (_: Exception) { return emptyList() }
    if (lessonCount <= 0) return emptyList()
    val targetDow = DayOfWeek(dayOrdinal)
    var date = startD
    while (date.dayOfWeek != targetDow) date = date.plus(1, DateTimeUnit.DAY)
    return (0 until lessonCount).map { i -> date.plus(i, DateTimeUnit.WEEK) }
}

fun computeLessonEndTime(startTime: LocalTime, durationMinutes: Int): LocalTime {
    val endMinutes = (startTime.hour * 60 + startTime.minute + durationMinutes) % (24 * 60)
    return LocalTime(endMinutes / 60, endMinutes % 60)
}

fun parseTimeOrNull(timeStr: String): LocalTime? =
    if (timeStr.isNotBlank()) try { LocalTime.parse(timeStr) } catch (_: Exception) { null } else null

/** Datum lekce na daném indexu po aplikaci případného override. */
private fun resolveLessonDate(dates: List<LocalDate>, lessonDateOverrides: Map<Int, String>, index: Int): LocalDate {
    val dateStr = lessonDateOverrides[index] ?: return dates[index]
    return try { LocalDate.parse(dateStr) } catch (_: Exception) { dates[index] }
}

/**
 * Indexy lekcí, které se skutečně vytvoří — vygenerovaná data bez vyřazených.
 * Overrides i příznak "lze individuálně" zůstávají klíčované původním indexem,
 * takže vyřazení lekce neposune nastavení ostatních.
 */
fun keptLessonIndices(dates: List<LocalDate>, excludedIndices: Set<Int>): List<Int> =
    dates.indices.filter { it !in excludedIndices }

/** Skutečná data lekcí (po override, bez vyřazených), seřazená vzestupně. */
fun effectiveSeriesDates(
    dates: List<LocalDate>,
    lessonDateOverrides: Map<Int, String>,
    excludedIndices: Set<Int>,
): List<LocalDate> = keptLessonIndices(dates, excludedIndices)
    .map { resolveLessonDate(dates, lessonDateOverrides, it) }
    .sorted()

/** Sestaví konfiguraci lekcí; null pokud chybí čas začátku, trvání šablony, nebo nezbyla žádná lekce. */
fun buildSeriesLessonConfigs(
    dates: List<LocalDate>,
    lessonDateOverrides: Map<Int, String>,
    lessonStartTime: LocalTime?,
    durationMinutes: Int?,
    lessonDropIn: Map<Int, Boolean>,
    excludedIndices: Set<Int> = emptySet(),
): List<LessonConfig>? {
    if (lessonStartTime == null || durationMinutes == null) return null
    val kept = keptLessonIndices(dates, excludedIndices)
    if (kept.isEmpty()) return null
    val endT = computeLessonEndTime(lessonStartTime, durationMinutes)
    return kept.map { i ->
        val date = resolveLessonDate(dates, lessonDateOverrides, i)
        LessonConfig(
            startDateTime = LocalDateTime(date, lessonStartTime),
            endDateTime = LocalDateTime(date, endT),
            isDropIn = lessonDropIn[i] ?: false,
        )
    }
}

enum class SeriesCreateValidationError { MissingDates, MissingTitle, MissingOwnerEmail, StartDateFormat, EndDateFormat, EndBeforeStart }

sealed interface SeriesCreateValidation {
    data class Valid(val startDate: LocalDate, val endDate: LocalDate) : SeriesCreateValidation
    data class Invalid(val error: SeriesCreateValidationError) : SeriesCreateValidation
}

fun validateSeriesCreateForm(
    startDate: String,
    endDate: String,
    title: String,
    ownerEmails: List<String>,
): SeriesCreateValidation {
    if (startDate.isBlank() || endDate.isBlank()) return SeriesCreateValidation.Invalid(SeriesCreateValidationError.MissingDates)
    if (title.isBlank()) return SeriesCreateValidation.Invalid(SeriesCreateValidationError.MissingTitle)
    if (parseOwnerEmails(ownerEmails).isEmpty()) return SeriesCreateValidation.Invalid(SeriesCreateValidationError.MissingOwnerEmail)
    val parsedStart = try { LocalDate.parse(startDate) } catch (_: Exception) { return SeriesCreateValidation.Invalid(SeriesCreateValidationError.StartDateFormat) }
    val parsedEnd = try { LocalDate.parse(endDate) } catch (_: Exception) { return SeriesCreateValidation.Invalid(SeriesCreateValidationError.EndDateFormat) }
    if (parsedEnd < parsedStart) return SeriesCreateValidation.Invalid(SeriesCreateValidationError.EndBeforeStart)
    return SeriesCreateValidation.Valid(parsedStart, parsedEnd)
}

fun buildCreateEventSeriesRequest(
    form: EventSeriesCreateFormData,
    definitionId: Uuid,
    startDate: LocalDate,
    endDate: LocalDate,
    lessonCount: Int,
    lessonDayOfWeek: DayOfWeek?,
    lessonStartTime: LocalTime?,
    lessonEndTime: LocalTime?,
    customLessons: List<LessonConfig>?,
    reservationDeadline: Duration?,
    isPublished: Boolean,
): CreateEventSeriesRequest = CreateEventSeriesRequest(
    definitionId = definitionId,
    title = form.title,
    description = form.description,
    ownerEmails = parseOwnerEmails(form.ownerEmails),
    price = form.price,
    lessonPrice = form.lessonPrice?.takeIf { it > 0 },
    capacity = form.capacity,
    waitlistCapacity = form.waitlistCapacity,
    startDate = startDate,
    endDate = endDate,
    lessonCount = lessonCount,
    allowedPaymentTypes = buildList {
        if (form.allowBankTransfer) add(PaymentType.BANK_TRANSFER)
        if (form.allowOnSite) add(PaymentType.ON_SITE)
    },
    lessonDayOfWeek = lessonDayOfWeek,
    lessonStartTime = lessonStartTime,
    lessonEndTime = lessonEndTime,
    customLessons = customLessons,
    customFields = form.customFields,
    showAttendeeCount = form.showAttendeeCount,
    allowMultipleSeats = form.allowMultipleSeats,
    reservationDeadline = reservationDeadline,
    reservationDeadlineMessage = form.deadlineMessage.takeIf { it.isNotBlank() },
    isPublished = isPublished,
)

// --- UseCase třídy (tenké, vrací Either) ---

class EventSeriesCreateQueries(private val event: EventServiceInterface) {
    suspend fun definitions() = event.getAllDefinitions()
}

class EventSeriesCreateMutations(private val admin: AdminServiceInterface) {
    suspend fun create(request: CreateEventSeriesRequest) = admin.createEventSeries(request)
}
