package cz.svitaninymburk.projects.reservations.slot.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import cz.svitaninymburk.projects.reservations.RpcSerializersModules
import cz.svitaninymburk.projects.reservations.error.ReservationError
import cz.svitaninymburk.projects.reservations.reservation.Reservation
import cz.svitaninymburk.projects.reservations.reservation.ReservationTarget
import cz.svitaninymburk.projects.reservations.service.ReservationServiceInterface
import cz.svitaninymburk.projects.reservations.slot.DayAvailability
import cz.svitaninymburk.projects.reservations.slot.SlotSegment
import cz.svitaninymburk.projects.reservations.slot.SlotService
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotQueries
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotPick
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotSelection
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.SlotSubmitter
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.availabilityRange
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.initialDay
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.invalidatesSelection
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.isSelectionLongEnough
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.keepPick
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.maxSeatsFor
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.pickLast
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.pickStart
import cz.svitaninymburk.projects.reservations.slot.ui.usecase.selectionTarget
import cz.svitaninymburk.projects.reservations.ui.reservation.ReservationSubmittingModel
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitUseCase
import cz.svitaninymburk.projects.reservations.ui.reservation.usecase.ReservationSubmitter
import dev.kilua.core.IComponent
import dev.kilua.rpc.getService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

sealed interface SlotServicesState {
    data object Loading : SlotServicesState
    data class Loaded(val services: List<SlotService>) : SlotServicesState
    data object Error : SlotServicesState
}

sealed interface SlotDaysState {
    data object Loading : SlotDaysState
    data class Loaded(val days: List<DayAvailability>) : SlotDaysState
    data object Error : SlotDaysState
}

/**
 * Stránka hlídání: výběr služby, počtu dětí, dne, příchodu a odchodu, pak stávající
 * rezervační formulář jádra. Počet dětí je před časem, protože určuje, které kroky jsou
 * volné; jeho změna nechá z výběru času to, co pořád platí. Změna dne výběr zruší.
 */
class SlotPageModel(
    scope: CoroutineScope,
    private val queries: SlotQueries,
    submitter: ReservationSubmitter,
    onReserved: (Reservation) -> Unit,
    /** „Dnes“ podle prohlížeče; server rozsah stejně ořízne podle sebe. */
    private val today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
) : ReservationSubmittingModel(scope, submitter, onReserved) {

    var servicesState: SlotServicesState by mutableStateOf(SlotServicesState.Loading); private set
    var service: SlotService? by mutableStateOf(null); private set
    var daysState: SlotDaysState by mutableStateOf(SlotDaysState.Loading); private set
    var selectedDate: LocalDate? by mutableStateOf(null); private set
    var seats by mutableIntStateOf(1); private set
    var pick: SlotPick? by mutableStateOf(null); private set
    var reservationTarget: ReservationTarget.Slot? by mutableStateOf(null); private set

    private var daysJob: Job? = null

    val days: List<DayAvailability> get() = (daysState as? SlotDaysState.Loaded)?.days.orEmpty()
    val segments: List<SlotSegment> get() = days.firstOrNull { it.date == selectedDate }?.segments.orEmpty()
    val selection: SlotSelection? get() = pick?.selection

    /** Výběr služby má smysl jen tehdy, když je z čeho vybírat. */
    val canChangeService: Boolean
        get() = ((servicesState as? SlotServicesState.Loaded)?.services?.size ?: 0) > 1

    val canContinue: Boolean
        get() {
            val current = selection ?: return false
            val service = service ?: return false
            return isSelectionLongEnough(current, segments, service)
        }

    fun load() {
        servicesState = SlotServicesState.Loading
        scope.launch {
            servicesState = try {
                val services = queries.services()
                services.singleOrNull()?.let { selectService(it) }
                SlotServicesState.Loaded(services)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SlotServicesState.Error
            }
        }
    }

    fun selectService(value: SlotService) {
        service = value
        seats = 1
        selectedDate = null
        clearSelection()
        loadDays(keepDay = false)
    }

    fun backToServices() {
        daysJob?.cancel()
        service = null
        reservationTarget = null
        clearSelection()
    }

    fun retryDays() = loadDays(keepDay = true)

    fun selectDay(date: LocalDate) {
        selectedDate = date
        clearSelection()
    }

    fun setSeats(value: Int) {
        val service = service ?: return
        val clamped = value.coerceIn(1, maxSeatsFor(service))
        if (clamped == seats) return
        seats = clamped
        pick = keepPick(pick, segments, clamped, service)
    }

    fun tapStart(index: Int) {
        val service = service ?: return
        pick = pickStart(pick, index, segments, seats, service)
    }

    /** [index] je poslední krok úseku, odchod je jeho konec. */
    fun tapEnd(index: Int) {
        val service = service ?: return
        val current = pick ?: return
        pick = pickLast(current, index, segments, seats, service)
    }

    fun openReservation() {
        val current = selection ?: return
        val service = service ?: return
        if (!canContinue) return
        reservationTarget = selectionTarget(service, current, segments)
    }

    fun closeReservation() {
        reservationTarget = null
    }

    override fun onRejected(error: ReservationError.CreateReservation) {
        if (!invalidatesSelection(error)) return
        reservationTarget = null
        clearSelection()
        loadDays(keepDay = true)
    }

    fun clearSelection() {
        pick = null
    }

    /** [keepDay] = zůstat na vybraném dni, pokud v novém výsledku pořád je. */
    private fun loadDays(keepDay: Boolean) {
        val service = service ?: return
        daysJob?.cancel()
        daysState = SlotDaysState.Loading
        daysJob = scope.launch {
            daysState = try {
                val loaded = queries.availability(service.id, availabilityRange(today, service))
                val stillThere = selectedDate?.takeIf { date -> keepDay && loaded.any { it.date == date } }
                selectedDate = stillThere ?: initialDay(loaded, seats, service.minDuration)
                SlotDaysState.Loaded(loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SlotDaysState.Error
            }
        }
    }
}

fun IComponent.buildSlotPageModel(scope: CoroutineScope, router: Router): SlotPageModel {
    val rpc = slotService()
    val reservations = getService<ReservationServiceInterface>(RpcSerializersModules)
    return SlotPageModel(
        scope = scope,
        queries = SlotQueries(rpc),
        submitter = ReservationSubmitUseCase(reservations, slotSubmitter = SlotSubmitter(rpc)),
        onReserved = { router.navigate("/reservation/${it.id}") },
    )
}
