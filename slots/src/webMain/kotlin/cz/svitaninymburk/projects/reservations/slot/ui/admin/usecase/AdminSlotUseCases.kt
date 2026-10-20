package cz.svitaninymburk.projects.reservations.slot.ui.admin.usecase

import arrow.core.Either
import cz.svitaninymburk.projects.reservations.event.CustomFieldDefinition
import cz.svitaninymburk.projects.reservations.event.TextFieldDefinition
import cz.svitaninymburk.projects.reservations.reservation.PaymentType
import cz.svitaninymburk.projects.reservations.slot.AvailabilityException
import cz.svitaninymburk.projects.reservations.slot.AvailabilityRule
import cz.svitaninymburk.projects.reservations.slot.SchedulePeriod
import cz.svitaninymburk.projects.reservations.slot.ScheduleProblem
import cz.svitaninymburk.projects.reservations.slot.SlotAdminServiceInterface
import cz.svitaninymburk.projects.reservations.slot.SlotReservationRow
import cz.svitaninymburk.projects.reservations.slot.SlotSegment
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.slot.SlotServiceProblem
import cz.svitaninymburk.projects.reservations.slot.TimeWindow
import cz.svitaninymburk.projects.reservations.slot.periodsToRules
import cz.svitaninymburk.projects.reservations.slot.rulesToPeriods
import cz.svitaninymburk.projects.reservations.slot.validateSchedule
import cz.svitaninymburk.projects.reservations.slot.validateSlotService
import cz.svitaninymburk.projects.reservations.slot.ui.admin.ADMIN_SLOTS_PATH
import cz.svitaninymburk.projects.reservations.slot.ui.admin.SlotAdminTab
import cz.svitaninymburk.projects.reservations.util.hourMinute
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

// --- Nastavení služby ---

/** Pole formuláře nastavení. Délky v minutách, uzávěrka v hodinách, horizont ve dnech. */
data class SlotServiceForm(
    val title: String = "",
    val description: String = "",
    val stepMinutes: Long = 30,
    /** 0 = bez minima. */
    val minMinutes: Int = 0,
    /** 0 = bez omezení (v modelu `maxDuration = null`). */
    val maxMinutes: Int = 0,
    val pricePerHour: Double? = null,
    val capacity: Int = 8,
    val maxSeats: Int = 3,
    /** 0 = bez uzávěrky. */
    val deadlineHours: Int = 0,
    val horizonDays: Int = 28,
    val bankTransfer: Boolean = true,
    val onSite: Boolean = true,
    val ownerEmails: List<String> = listOf(""),
    val customFields: List<CustomFieldDefinition> = emptyList(),
    val isPublished: Boolean = false,
)

/** Formulář nové služby s poli, která hlídání skoro vždy potřebuje. */
fun newServiceForm(childrenFieldLabel: String, noteFieldLabel: String) = SlotServiceForm(
    customFields = listOf(
        TextFieldDefinition(key = "children", label = childrenFieldLabel, isRequired = true, isMultiline = true),
        TextFieldDefinition(key = "note", label = noteFieldLabel, isMultiline = true),
    ),
)

fun formOf(service: SlotService) = SlotServiceForm(
    title = service.title,
    description = service.description,
    stepMinutes = service.step.inWholeMinutes,
    minMinutes = service.minDuration.inWholeMinutes.toInt(),
    maxMinutes = service.maxDuration?.inWholeMinutes?.toInt() ?: 0,
    pricePerHour = service.pricePerHour,
    capacity = service.capacity,
    maxSeats = service.maxSeatsPerBooking,
    deadlineHours = service.reservationDeadline?.inWholeHours?.toInt() ?: 0,
    horizonDays = service.bookingHorizon.inWholeDays.toInt(),
    bankTransfer = PaymentType.BANK_TRANSFER in service.allowedPaymentTypes,
    onSite = PaymentType.ON_SITE in service.allowedPaymentTypes,
    ownerEmails = service.ownerEmails.ifEmpty { listOf("") },
    customFields = service.customFields,
    isPublished = service.isPublished,
)

fun SlotServiceForm.toService(id: Uuid): SlotService = SlotService(
    id = id,
    title = title,
    description = description,
    step = stepMinutes.minutes,
    minDuration = minMinutes.minutes,
    maxDuration = maxMinutes.takeIf { it > 0 }?.minutes,
    pricePerHour = pricePerHour ?: 0.0,
    capacity = capacity,
    maxSeatsPerBooking = maxSeats,
    allowedPaymentTypes = buildList {
        if (bankTransfer) add(PaymentType.BANK_TRANSFER)
        if (onSite) add(PaymentType.ON_SITE)
    },
    customFields = customFields,
    ownerEmails = ownerEmails.map { it.trim() }.filter { it.isNotEmpty() },
    reservationDeadline = deadlineHours.takeIf { it > 0 }?.hours,
    bookingHorizon = horizonDays.days,
    isPublished = isPublished,
)

/** Stejné kontroly jako na serveru, ať formulář chyby ukáže dřív, než se odešle. */
fun formProblems(form: SlotServiceForm): List<SlotServiceProblem> = validateSlotService(form.toService(Uuid.NIL))

// --- Rozvrh ---

/** Okno dne tak, jak je v políčkách formuláře („08:00“); prázdné = nevyplněno. */
data class WindowDraft(val from: String, val to: String)

data class PeriodDraft(
    val validFrom: String,
    /** Prázdné = bez konce. */
    val validTo: String,
    val days: Map<DayOfWeek, List<WindowDraft>>,
) {
    fun windowsOf(day: DayOfWeek): List<WindowDraft> = days[day].orEmpty()

    fun withWindows(day: DayOfWeek, windows: List<WindowDraft>) = copy(days = days + (day to windows))

    /** Další okno začne tam, kde předchozí skončilo, ať se nemusí přepisovat celé. */
    fun withAddedWindow(day: DayOfWeek): PeriodDraft {
        val last = windowsOf(day).lastOrNull()
        val added = if (last == null) WindowDraft("08:00", "12:00") else WindowDraft(last.to, "")
        return withWindows(day, windowsOf(day) + added)
    }

    fun withoutWindow(day: DayOfWeek, index: Int) = withWindows(day, windowsOf(day).filterIndexed { i, _ -> i != index })

    fun withWindow(day: DayOfWeek, index: Int, window: WindowDraft) =
        withWindows(day, windowsOf(day).mapIndexed { i, w -> if (i == index) window else w })
}

data class ExceptionDraft(
    val id: Uuid,
    val date: String,
    val closed: Boolean,
    val from: String,
    val to: String,
)

data class ScheduleDraft(val periods: List<PeriodDraft>, val exceptions: List<ExceptionDraft>) {

    fun withPeriod(index: Int, period: PeriodDraft) = copy(periods = periods.mapIndexed { i, p -> if (i == index) period else p })
    fun withoutPeriod(index: Int) = copy(periods = periods.filterIndexed { i, _ -> i != index })

    /**
     * Nové období navazuje na poslední (den po jeho konci) a převezme jeho týden — změna
     * otevírací doby obvykle mění jen pár časů. Poslední bez konce se ukončí den předem.
     */
    fun withAddedPeriod(today: LocalDate): ScheduleDraft {
        val last = periods.lastOrNull() ?: return copy(periods = listOf(PeriodDraft(today.toString(), "", emptyMap())))
        val lastEnd = parseDateOrNull(last.validTo)
        val start = lastEnd?.plus(1, DateTimeUnit.DAY) ?: maxOf(today, parseDateOrNull(last.validFrom) ?: today).plus(1, DateTimeUnit.DAY)
        val closedLast = if (lastEnd == null) last.copy(validTo = start.plus(-1, DateTimeUnit.DAY).toString()) else last
        return copy(periods = periods.dropLast(1) + closedLast + PeriodDraft(start.toString(), "", last.days))
    }

    fun withException(index: Int, exception: ExceptionDraft) =
        copy(exceptions = exceptions.mapIndexed { i, e -> if (i == index) exception else e })
    fun withoutException(index: Int) = copy(exceptions = exceptions.filterIndexed { i, _ -> i != index })
    fun withAddedException(date: LocalDate) =
        copy(exceptions = exceptions + ExceptionDraft(Uuid.random(), date.toString(), closed = true, from = "", to = ""))
}

fun scheduleDraftOf(rules: List<AvailabilityRule>, exceptions: List<AvailabilityException>) = ScheduleDraft(
    periods = rulesToPeriods(rules).map { period ->
        PeriodDraft(
            validFrom = period.validFrom.toString(),
            validTo = period.validTo?.toString().orEmpty(),
            days = period.windows.mapValues { (_, windows) -> windows.map { WindowDraft(it.from.inputValue, it.to.inputValue) } },
        )
    },
    exceptions = exceptions.sortedBy { it.date }.map {
        ExceptionDraft(it.id, it.date.toString(), it.closed, it.from?.inputValue.orEmpty(), it.to?.inputValue.orEmpty())
    },
)

/** Rozvrh k uložení, nebo co je ve formuláři špatně (stejné kontroly jako na serveru). */
fun ScheduleDraft.toSchedule(serviceId: Uuid): Either<List<ScheduleProblem>, Pair<List<AvailabilityRule>, List<AvailabilityException>>> {
    var incomplete = false
    fun date(value: String) = parseDateOrNull(value).also { if (it == null) incomplete = true }
    fun time(value: String) = parseTimeOrNull(value).also { if (it == null) incomplete = true }

    val periods = periods.mapNotNull { draft ->
        val from = date(draft.validFrom)
        val to = if (draft.validTo.isBlank()) null else date(draft.validTo)
        val windows = draft.days.mapValues { (_, windows) ->
            windows.mapNotNull { w ->
                val start = time(w.from)
                val end = time(w.to)
                if (start != null && end != null) TimeWindow(start, end) else null
            }
        }
        from?.let { SchedulePeriod(it, to, windows) }
    }
    val parsedExceptions = exceptions.mapNotNull { draft ->
        val day = date(draft.date) ?: return@mapNotNull null
        if (draft.closed) {
            AvailabilityException(draft.id, serviceId, day, closed = true)
        } else {
            AvailabilityException(draft.id, serviceId, day, closed = false, from = time(draft.from), to = time(draft.to))
        }
    }
    if (incomplete) return Either.Left(listOf(ScheduleProblem.INCOMPLETE))

    val rules = periodsToRules(serviceId, periods)
    // Období bez jediného okna by se do pravidel nepropsalo a po uložení zmizelo — to by mátlo.
    val problems = validateSchedule(rules, parsedExceptions) +
        listOfNotNull(ScheduleProblem.INCOMPLETE.takeIf { periods.any { p -> p.windows.values.all { it.isEmpty() } } })
    return if (problems.isEmpty()) Either.Right(rules to parsedExceptions) else Either.Left(problems.distinct())
}

private val LocalTime.inputValue: String get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun parseDateOrNull(value: String): LocalDate? = try { LocalDate.parse(value.trim()) } catch (_: IllegalArgumentException) { null }
private fun parseTimeOrNull(value: String): LocalTime? = try { LocalTime.parse(value.trim()) } catch (_: IllegalArgumentException) { null }

// --- Den ---

fun shiftDay(date: LocalDate, days: Int): LocalDate = date.plus(days, DateTimeUnit.DAY)

fun occupiedSeats(segment: SlotSegment): Int = (segment.capacity - segment.free).coerceAtLeast(0)

/** „9:00–11:30“ */
fun reservationTimeLabel(row: SlotReservationRow): String = "${row.start.time.hourMinute}–${row.end.time.hourMinute}"

fun dayChildrenTotal(rows: List<SlotReservationRow>): Int = rows.sumOf { it.seatCount }

/**
 * Proklik do admin přehledu rezervací s vyhledanou rezervací. Hledá se podle VS,
 * rezervace bez VS (zdarma, na místě) podle e-mailu.
 */
fun adminReservationLink(row: SlotReservationRow): String {
    val query = row.variableSymbol?.takeIf { it.isNotBlank() } ?: row.contactEmail
    return "/admin/reservations?search=${encodeQueryValue(query)}"
}

/** Query parametr se záložkou detailu služby: `/admin/slots/{id}?tab=schedule`. */
const val SLOT_TAB_PARAM = "tab"

/** Adresa detailu služby, volitelně rovnou na dané záložce. */
fun adminSlotServiceLink(id: Uuid, tab: SlotAdminTab? = null): String =
    "$ADMIN_SLOTS_PATH/$id" + (tab?.let { "?$SLOT_TAB_PARAM=${it.name.lowercase()}" } ?: "")

/** Záložka z hodnoty query parametru; neznámá nebo chybějící = výchozí „Den“. */
fun slotAdminTabOf(value: String?): SlotAdminTab =
    SlotAdminTab.entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: SlotAdminTab.DAY

private fun encodeQueryValue(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val c = byte.toInt().toChar()
        if (byte >= 0 && (c.isLetterOrDigit() || c in "-_.~")) append(c)
        else append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
    }
}

// --- UseCase třídy (tenké) ---

class SlotAdminQueries(private val rpc: SlotAdminServiceInterface) {
    suspend fun services() = rpc.listServices()
    suspend fun detail(id: Uuid) = rpc.getService(id)
    suspend fun day(id: Uuid, date: LocalDate) = rpc.getDayOverview(id, date)
    suspend fun previewSchedule(id: Uuid, rules: List<AvailabilityRule>, exceptions: List<AvailabilityException>) =
        rpc.previewSchedule(id, rules, exceptions)
}

class SlotAdminMutations(private val rpc: SlotAdminServiceInterface) {
    suspend fun create(service: SlotService) = rpc.createService(service)
    suspend fun update(service: SlotService) = rpc.updateService(service)
    suspend fun setPublished(id: Uuid, published: Boolean) = rpc.setPublished(id, published)
    suspend fun delete(id: Uuid, refund: Boolean) = rpc.deleteService(id, refund)
    suspend fun saveSchedule(id: Uuid, rules: List<AvailabilityRule>, exceptions: List<AvailabilityException>) =
        rpc.saveSchedule(id, rules, exceptions)
}
